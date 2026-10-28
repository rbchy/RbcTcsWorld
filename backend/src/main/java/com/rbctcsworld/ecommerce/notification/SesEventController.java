package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * POST /api/notifications/ses/events  (no login; the HTTPS subscription of the SNS topic that SES reports to)
 *
 * Who may call: only AWS. The message must be signed by SNS (SnsMessageVerifier) and come from the one configured
 * topic (SES_SNS_TOPIC_ARN); otherwise 400, and 503 while no topic is configured.
 *  - SubscriptionConfirmation: the shop confirms the subscription by opening SubscribeURL (an SNS address);
 *  - Notification: a permanent bounce or a complaint puts each address on the suppression list; temporary bounces,
 *    deliveries and anything else change nothing.
 */
@RestController
class SesEventController {

    private static final Logger log = LoggerFactory.getLogger(SesEventController.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    static final String NOT_SNS = "Not an SNS message";

    private final EmailSuppressions suppressions;
    private final SnsMessageVerifier verifier;
    private final SnsHttp http;
    private final String topicArn;

    SesEventController(EmailSuppressions suppressions, SnsHttp http,
                       @Value("${app.notifications.ses.sns-topic-arn:}") String topicArn) {
        this.suppressions = suppressions;
        this.verifier = new SnsMessageVerifier(http);
        this.http = http;
        this.topicArn = topicArn;
    }

    @PostMapping("/api/notifications/ses/events")
    Map<String, Boolean> event(@RequestBody(required = false) String payload) {
        if (topicArn == null || topicArn.isBlank()) {
            throw new ServiceUnavailableException("SES event reports are not configured");
        }
        JsonNode m = parse(payload);
        if (!topicArn.equals(text(m, "TopicArn"))) {
            throw new BusinessRuleException(NOT_SNS);
        }
        verifier.verify(m);
        String type = text(m, "Type");
        if ("SubscriptionConfirmation".equals(type)) {
            http.apply(SnsMessageVerifier.snsUrl(text(m, "SubscribeURL")));
            log.info("SNS subscription to {} confirmed", topicArn);
        } else if ("Notification".equals(type)) {
            onSesEvent(parse(text(m, "Message")));
        }
        return Map.of("received", true);
    }

    private void onSesEvent(JsonNode e) {
        String kind = text(e, "notificationType");
        if ("Bounce".equals(kind) && "Permanent".equals(text(e.path("bounce"), "bounceType"))) {
            for (JsonNode r : e.path("bounce").path("bouncedRecipients")) {
                add(text(r, "emailAddress"), EmailSuppressions.BOUNCE, text(r, "diagnosticCode"));
            }
        } else if ("Complaint".equals(kind)) {
            for (JsonNode r : e.path("complaint").path("complainedRecipients")) {
                add(text(r, "emailAddress"), EmailSuppressions.COMPLAINT, text(e.path("complaint"), "complaintFeedbackType"));
            }
        }
    }

    private void add(String email, String reason, String detail) {
        if (email != null && !email.isBlank() && suppressions.suppress(email, reason, detail)) {
            log.warn("{} will not be e-mailed again: {} ({})", email, reason, detail);
        }
    }

    private static JsonNode parse(String payload) {
        if (payload == null) {
            throw new BusinessRuleException(NOT_SNS);
        }
        try {
            JsonNode n = JSON.readTree(payload);
            if (!n.isObject()) {
                throw new BusinessRuleException(NOT_SNS);
            }
            return n;
        } catch (JacksonException e) {
            throw new BusinessRuleException(NOT_SNS);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString() : null;
    }
}
