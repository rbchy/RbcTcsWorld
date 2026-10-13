package com.rbctcsworld.ecommerce.payment;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The two Stripe API calls this shop needs, over plain HTTP (no SDK): our own request building and
 * response parsing is exactly what the contract tests check against stripe-mock and recorded answers.
 *
 *  - POST /v1/payment_intents  (create + confirm in one call)
 *  - POST /v1/refunds
 *
 * Stripe expects form encoding, amounts in the smallest unit (cents) and an Idempotency-Key on every
 * POST: a retried request with the same key returns the first result instead of charging again.
 */
class StripeClient {

    record PaymentIntent(String id, String status, long amount, String currency) {
    }

    record Refund(String id, String status, long amount) {
    }

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final URI base;
    private final String secretKey;
    private final Duration timeout;
    private final HttpClient http;

    StripeClient(String baseUrl, String secretKey, Duration timeout) {
        this.base = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.secretKey = secretKey;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
    }

    PaymentIntent createPaymentIntent(long amountCents, String currency, String paymentMethod, Long orderId,
                                      String idempotencyKey) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("amount", Long.toString(amountCents));
        form.put("currency", currency);
        form.put("payment_method", paymentMethod);
        form.put("confirm", "true");
        form.put("automatic_payment_methods[enabled]", "true");
        form.put("automatic_payment_methods[allow_redirects]", "never");   // no return_url: card only
        form.put("metadata[order_id]", String.valueOf(orderId));
        JsonNode n = post("/v1/payment_intents", form, idempotencyKey);
        return new PaymentIntent(text(n, "id"), text(n, "status"), n.path("amount").asLong(), text(n, "currency"));
    }

    Refund createRefund(String paymentIntentId, long amountCents, Long orderId, String idempotencyKey) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("payment_intent", paymentIntentId);
        form.put("amount", Long.toString(amountCents));
        form.put("metadata[order_id]", String.valueOf(orderId));
        JsonNode n = post("/v1/refunds", form, idempotencyKey);
        return new Refund(text(n, "id"), text(n, "status"), n.path("amount").asLong());
    }

    static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private JsonNode post(String path, Map<String, String> form, String idempotencyKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new StripeException(0, "api_connection_error", null, null, "Stripe not reachable: " + e, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StripeException(0, "api_connection_error", null, null, "Interrupted", null);
        }
        JsonNode body = parse(response);
        if (response.statusCode() / 100 == 2) {
            return body;
        }
        JsonNode error = body.path("error");
        throw new StripeException(response.statusCode(), text(error, "type"), text(error, "code"),
                text(error, "decline_code"), text(error, "message"), text(error.path("payment_intent"), "id"));
    }

    private static JsonNode parse(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (JacksonException e) {
            throw new StripeException(response.statusCode(), "api_error", null, null,
                    "Unreadable answer from Stripe (HTTP " + response.statusCode() + ")", null);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString() : null;
    }
}
