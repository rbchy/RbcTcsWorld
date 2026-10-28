package com.rbctcsworld.ecommerce.notification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Signature Version 4 for one JSON POST without a query string - all SES needs, written out instead of pulling in
 * the AWS SDK. Checked against signatures computed by botocore (AwsSigV4Test).
 *
 *   canonical request = METHOD \n path \n (empty query) \n headers \n signed header names \n sha256(body)
 *   string to sign    = AWS4-HMAC-SHA256 \n time \n date/region/service/aws4_request \n sha256(canonical request)
 *   signing key       = HMAC chain "AWS4"+secret -> date -> region -> service -> "aws4_request"
 */
final class AwsSigV4 {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final HexFormat HEX = HexFormat.of();

    private AwsSigV4() {
    }

    /** The headers to add to the request: X-Amz-Date, Authorization, and X-Amz-Security-Token for temporary keys. */
    static Map<String, String> sign(URI uri, String contentType, String body, String region, String service,
                                    String accessKeyId, String secretAccessKey, String sessionToken, Instant now) {
        String amzDate = AMZ_DATE.format(now);
        String date = amzDate.substring(0, 8);
        TreeMap<String, String> headers = new TreeMap<>();            // sorted by lower-case name
        headers.put("content-type", contentType);
        headers.put("host", uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort());
        headers.put("x-amz-date", amzDate);
        boolean temporary = sessionToken != null && !sessionToken.isBlank();
        if (temporary) {
            headers.put("x-amz-security-token", sessionToken);
        }
        StringBuilder canonicalHeaders = new StringBuilder();
        headers.forEach((k, v) -> canonicalHeaders.append(k).append(':').append(v.trim()).append('\n'));
        String signedHeaders = String.join(";", headers.keySet());
        String canonicalRequest = "POST\n" + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath()) + "\n\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256Hex(body);
        String scope = date + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);
        byte[] key = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        String signature = HEX.formatHex(hmac(key, stringToSign));

        Map<String, String> out = new LinkedHashMap<>();
        out.put("X-Amz-Date", amzDate);
        out.put("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + scope + ", SignedHeaders="
                + signedHeaders + ", Signature=" + signature);
        if (temporary) {
            out.put("X-Amz-Security-Token", sessionToken);
        }
        return out;
    }

    static String sha256Hex(String text) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
