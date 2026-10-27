package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Stripe webhooks: proving that a call really comes from Stripe, and reading the PaymentIntent events.
 *
 * Signature (https://docs.stripe.com/webhooks#verify-manually): the Stripe-Signature header is
 * "t=<unix seconds>,v1=<hex>[,v1=<hex>...]"; v1 = HMAC-SHA256(webhook secret, "<t>.<raw body>"). The body must be
 * the exact bytes Stripe sent, the comparison is constant-time, and a timestamp older (or newer) than five minutes is
 * refused, so a recorded call cannot be replayed later. Anything wrong is a 400, and nothing is changed.
 */
public final class StripeWebhook {

    /** Stripe's own default tolerance. */
    public static final Duration TOLERANCE = Duration.ofMinutes(5);
    static final String INVALID = "Invalid Stripe signature";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private StripeWebhook() {
    }

    /** One event; payment = the PaymentIntent for payment_intent.* events, null for every other type. */
    public record Event(String id, String type, PaymentGateway.HostedStatus payment) {
    }

    /** 400 unless the header carries a valid signature of exactly this payload, made within the tolerance. */
    public static void verify(String payload, String signatureHeader, String secret, Instant now) {
        if (payload == null || signatureHeader == null || secret == null || secret.isBlank()) {
            throw new BusinessRuleException(INVALID);
        }
        Long timestamp = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    throw new BusinessRuleException(INVALID);
                }
            } else if (kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new BusinessRuleException(INVALID);
        }
        if (Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(TOLERANCE) > 0) {
            throw new BusinessRuleException(INVALID);
        }
        byte[] expected = sign(secret, timestamp + "." + payload);
        for (String candidate : signatures) {
            byte[] given;
            try {
                given = HexFormat.of().parseHex(candidate);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, given)) {
                return;
            }
        }
        throw new BusinessRuleException(INVALID);
    }

    /** The v1 signature Stripe would send; the tests use it to sign their own events. */
    public static String signatureHeader(String payload, String secret, long timestamp) {
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(sign(secret, timestamp + "." + payload));
    }

    static byte[] sign(String secret, String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {   // HmacSHA256 is in every JDK
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads an event (call {@link #verify} first). For payment_intent.* events {@link Event#payment()} holds the
     * PaymentIntent; for anything else it is null. A body that is not a Stripe event is a 400.
     */
    public static Event parse(String payload) {
        JsonNode n;
        try {
            n = JSON.readTree(payload);
        } catch (JacksonException e) {
            throw new BusinessRuleException("Not a Stripe event");
        }
        String id = text(n, "id");
        String type = text(n, "type");
        if (id == null || type == null) {
            throw new BusinessRuleException("Not a Stripe event");
        }
        JsonNode object = n.path("data").path("object");
        PaymentGateway.HostedStatus payment = null;
        if (type.startsWith("payment_intent.") && "payment_intent".equals(text(object, "object"))) {
            JsonNode error = object.path("last_payment_error");
            String declineCode = text(error, "decline_code");
            String code = text(error, "code");
            payment = new PaymentGateway.HostedStatus(text(object, "id"), text(object, "status"),
                    object.path("amount").asLong(), StripePaymentGateway.orderId(text(object.path("metadata"), "order_id")),
                    declineCode == null && code == null ? null : StripePaymentGateway.reason(declineCode, code));
        }
        return new Event(id, type, payment);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString() : null;
    }
}
