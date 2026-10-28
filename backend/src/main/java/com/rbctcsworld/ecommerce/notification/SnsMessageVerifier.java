package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Proves an Amazon SNS message comes from AWS: its signature (SignatureVersion 1 = SHA1withRSA, 2 = SHA256withRSA)
 * over the documented fields, checked with the certificate at SigningCertURL - and that URL must be an HTTPS address
 * of SNS itself, or anyone could sign with their own certificate. Certificates are cached by URL.
 */
class SnsMessageVerifier {

    static final String INVALID = "Invalid SNS message signature";
    /** sns.<region>.amazonaws.com (and .com.cn in China): nobody else may provide the certificate. */
    static final Pattern SNS_HOST = Pattern.compile("^sns\\.[a-z0-9-]+\\.amazonaws\\.com(\\.cn)?$");

    private static final List<String> NOTIFICATION = List.of("Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type");
    private static final List<String> SUBSCRIPTION = List.of("Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type");

    private final Function<URI, byte[]> download;
    private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();

    /** download: GET of an already-checked SNS URL (SnsHttp in production, a fixture in tests). */
    SnsMessageVerifier(Function<URI, byte[]> download) {
        this.download = download;
    }

    void verify(JsonNode m) {
        String version = text(m, "SignatureVersion");
        String algorithm = "1".equals(version) ? "SHA1withRSA" : "2".equals(version) ? "SHA256withRSA" : null;
        String signature = text(m, "Signature");
        String type = text(m, "Type");
        if (algorithm == null || signature == null || type == null) {
            throw new BusinessRuleException(INVALID);
        }
        URI certUrl = snsUrl(text(m, "SigningCertURL"));
        if (!certUrl.getPath().endsWith(".pem")) {
            throw new BusinessRuleException(INVALID);
        }
        try {
            Signature s = Signature.getInstance(algorithm);
            s.initVerify(keys.computeIfAbsent(certUrl.toString(), u -> publicKey(download.apply(certUrl))));
            s.update(canonical(m, "Notification".equals(type) ? NOTIFICATION : SUBSCRIPTION).getBytes(StandardCharsets.UTF_8));
            if (!s.verify(Base64.getDecoder().decode(signature))) {
                throw new BusinessRuleException(INVALID);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessRuleException(INVALID);
        }
    }

    /** "Name\nvalue\n" for every present field, in the order AWS documents. */
    static String canonical(JsonNode m, List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            String v = text(m, f);
            if (v != null) {
                sb.append(f).append('\n').append(v).append('\n');
            }
        }
        return sb.toString();
    }

    /** An https URL on an SNS host, or 400: used for the certificate and for confirming the subscription. */
    static URI snsUrl(String url) {
        try {
            URI u = URI.create(url);
            if ("https".equals(u.getScheme()) && u.getHost() != null && SNS_HOST.matcher(u.getHost()).matches()
                    && u.getPort() == -1 && u.getUserInfo() == null) {
                return u;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            // fall through
        }
        throw new BusinessRuleException(INVALID);
    }

    static PublicKey publicKey(byte[] pem) {
        try {
            return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pem)).getPublicKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not an X.509 certificate", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString() : null;
    }
}
