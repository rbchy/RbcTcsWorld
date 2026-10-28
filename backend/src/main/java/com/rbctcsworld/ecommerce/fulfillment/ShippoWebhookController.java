package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * POST /api/shipping/shippo/webhook?token=...  (no login; registered in Shippo as the "track_updated" webhook URL)
 *
 * Shippo does not sign its webhooks, so the URL carries a secret (SHIPPO_WEBHOOK_TOKEN), compared in constant time:
 * no token 400, none configured 503, a wrong one 400. Even then the body is only a hint: FulfillmentService reads the parcel's
 * status from Shippo itself before it marks anything delivered.
 */
@RestController
class ShippoWebhookController {

    static final String INVALID = "Invalid webhook token";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final FulfillmentService fulfillment;
    private final String secret;

    ShippoWebhookController(FulfillmentService fulfillment, @Value("${app.shipping.shippo.webhook-token:}") String secret) {
        this.fulfillment = fulfillment;
        this.secret = secret;
    }

    @PostMapping("/api/shipping/shippo/webhook")
    Map<String, Boolean> webhook(@RequestParam(name = "token", required = false) String token,
                                 @RequestBody(required = false) String payload) {
        if (token == null) {
            throw new BusinessRuleException(INVALID);
        }
        if (secret == null || secret.isBlank()) {
            throw new ServiceUnavailableException("Shippo webhooks are not configured");
        }
        if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessRuleException(INVALID);
        }
        String trackingNumber = trackingNumber(payload);
        if (trackingNumber != null) {
            fulfillment.onTrackingUpdate(trackingNumber);
        }
        return Map.of("received", true);
    }

    /** The tracking number of a track_updated event; null for anything else (answered 200, so Shippo stops resending). */
    static String trackingNumber(String payload) {
        if (payload == null) {
            return null;
        }
        JsonNode n;
        try {
            n = JSON.readTree(payload);
        } catch (JacksonException e) {
            throw new BusinessRuleException("Not a Shippo event");
        }
        if (!"track_updated".equals(text(n, "event"))) {
            return null;
        }
        String number = text(n.path("data"), "tracking_number");
        return number == null || number.isBlank() ? null : number;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString() : null;
    }
}
