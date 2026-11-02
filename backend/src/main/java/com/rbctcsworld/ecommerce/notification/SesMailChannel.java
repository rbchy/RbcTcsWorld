package com.rbctcsworld.ecommerce.notification;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Amazon SES (API v2, SendEmail) over HTTPS, signed with AWS Signature V4 - no SDK, no SMTP port to open.
 * The sender is the store's own domain (verified in SES with DKIM). Any answer but 200 throws, so the outbox
 * (NotificationDeliveryService) retries: throttling, an SES outage and a rejected message are all retried, then FAILED.
 * An optional configuration set routes bounces and complaints to SNS -> SesEventController.
 */
class SesMailChannel implements MailChannel {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final URI endpoint;
    private final String region;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;
    private final String from;
    private final String configurationSet;
    private final Duration timeout;
    private final Clock clock;
    private final HttpClient http;
    /** The name in the header of the HTML part (ADR-0025); app.store.name in production. */
    private String storeName = "RbcTcsWorld Shop";

    SesMailChannel(String endpointBase, String region, String accessKeyId, String secretAccessKey, String sessionToken,
                   String from, String configurationSet, Duration timeout, Clock clock) {
        String base = endpointBase.endsWith("/") ? endpointBase.substring(0, endpointBase.length() - 1) : endpointBase;
        this.endpoint = URI.create(base + "/v2/email/outbound-emails");
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
        this.from = from;
        this.configurationSet = configurationSet;
        this.timeout = timeout;
        this.clock = clock;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
    }

    @Override
    public void send(String to, String subject, String body) {
        String json = JSON.writeValueAsString(request(to, subject, body));
        HttpRequest.Builder b = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        AwsSigV4.sign(endpoint, "application/json", json, region, "ses", accessKeyId, secretAccessKey, sessionToken,
                clock.instant()).forEach(b::header);
        HttpResponse<String> response;
        try {
            response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("SES not reachable: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SES: interrupted", e);
        }
        if (response.statusCode() != 200) {
            String type = response.headers().firstValue("x-amzn-ErrorType").orElse("").split(":")[0];
            throw new IllegalStateException("SES " + response.statusCode() + (type.isEmpty() ? "" : " " + type) + ": " + message(response.body()));
        }
    }

    Map<String, Object> request(String to, String subject, String body) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("FromEmailAddress", from);
        req.put("Destination", Map.of("ToAddresses", List.of(to)));
        req.put("Content", Map.of("Simple", Map.of(
                "Subject", Map.of("Data", subject, "Charset", "UTF-8"),
                "Body", body(subject, body))));
        if (configurationSet != null && !configurationSet.isBlank()) {
            req.put("ConfigurationSetName", configurationSet);
        }
        return req;
    }

    /** Text first, then its HTML twin (ADR-0025): SES sends both as multipart/alternative. */
    private Map<String, Object> body(String subject, String text) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("Text", Map.of("Data", text, "Charset", "UTF-8"));
        b.put("Html", Map.of("Data", EmailHtml.render(storeName, subject, text), "Charset", "UTF-8"));
        return b;
    }

    SesMailChannel storeName(String name) {
        if (name != null && !name.isBlank()) this.storeName = name.trim();
        return this;
    }

    /** SES error bodies: {"message": "..."} (sometimes "Message"); anything else is shown as it came. */
    static String message(String body) {
        try {
            JsonNode n = JSON.readTree(body);
            for (String field : new String[] {"message", "Message"}) {
                if (n != null && n.path(field).isString()) {
                    return n.path(field).asString();
                }
            }
        } catch (JacksonException ignored) {
            // not JSON: fall through
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    @Override
    public String name() {
        return "ses";
    }
}
