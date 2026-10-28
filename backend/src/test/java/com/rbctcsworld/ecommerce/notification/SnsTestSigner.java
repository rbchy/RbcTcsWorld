package com.rbctcsworld.ecommerce.notification;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test SNS messages signed with the TEST-ONLY key in src/test/resources/ses (self-signed, trusted by nothing).
 * The fixtures sns_*.json were signed outside Java (openssl); this signs variants for edge cases.
 */
public final class SnsTestSigner {

    public static final String TOPIC = "arn:aws:sns:us-east-1:123456789012:rbctcsworld-ses-events";
    public static final String CERT_URL = "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-test.pem";

    private SnsTestSigner() {
    }

    public static byte[] resource(String name) {
        try (InputStream in = SnsTestSigner.class.getResourceAsStream("/ses/" + name)) {
            return in.readAllBytes();
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("missing test resource " + name, e);
        }
    }

    public static String fixture(String name) {
        return new String(resource(name), StandardCharsets.UTF_8);
    }

    /** The certificate "download": only the test certificate URL answers. */
    public static byte[] download(URI uri) {
        if (!uri.toString().equals(CERT_URL)) {
            throw new IllegalStateException("unexpected download " + uri);
        }
        return resource("sns-test-cert.pem");
    }

    /** A signed Notification (SignatureVersion 2) carrying this SES event. */
    public static String notification(String messageId, String sesEventJson) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Type", "Notification");
        m.put("MessageId", messageId);
        m.put("TopicArn", TOPIC);
        m.put("Message", sesEventJson);
        m.put("Timestamp", "2026-10-28T03:00:01.000Z");
        m.put("SignatureVersion", "2");
        m.put("SigningCertURL", CERT_URL);
        StringBuilder canonical = new StringBuilder();
        for (String k : List.of("Message", "MessageId", "Timestamp", "TopicArn", "Type")) {
            canonical.append(k).append('\n').append(m.get(k)).append('\n');
        }
        m.put("Signature", sign(canonical.toString()));
        return JsonMapper.builder().build().writeValueAsString(m);
    }

    static String sign(String text) {
        try {
            String pem = fixture("sns-test-key.pem").replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(key);
            s.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }
}
