package com.rbctcsworld.ecommerce.notification;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AWS Signature V4, checked against signatures computed outside Java by botocore (the AWS SDK for Python). */
class AwsSigV4Test {

    private static final URI SES = URI.create("https://email.us-east-1.amazonaws.com/v2/email/outbound-emails");
    private static final String BODY = "{\"FromEmailAddress\":\"RbcTcsWorld Shop <orders@shop.example.com>\","
            + "\"Destination\":{\"ToAddresses\":[\"ada@example.com\"]},\"Content\":{\"Simple\":{\"Subject\":{\"Data\":"
            + "\"Your order\",\"Charset\":\"UTF-8\"},\"Body\":{\"Text\":{\"Data\":\"Thank you\",\"Charset\":\"UTF-8\"}}}}}";
    private static final String AKID = "AKIDEXAMPLE";
    private static final String SECRET = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
    private static final Instant T = Instant.parse("2026-10-28T03:00:00Z");

    // Known answers: botocore.auth.SigV4Auth(Credentials(AKID, SECRET[, token]), "ses", "us-east-1").add_auth(...)
    // on POST SES with Content-Type: application/json and BODY at 2026-10-28T03:00:00Z (docs/modules/AMAZON_SES_MAIL_BN.md)
    @Test
    void permanentKeysSignLikeTheAwsSdk() {
        Map<String, String> h = AwsSigV4.sign(SES, "application/json", BODY, "us-east-1", "ses", AKID, SECRET, null, T);
        assertThat(h).containsExactly(
                Map.entry("X-Amz-Date", "20261028T030000Z"),
                Map.entry("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261028/us-east-1/ses/aws4_request, "
                        + "SignedHeaders=content-type;host;x-amz-date, "
                        + "Signature=e8020761ca0b6ecd70234cd6e243da7f17f824819748a6ca7c0f44529eaa6a07"));
    }

    @Test
    void temporaryKeysSignTheSessionTokenToo() {
        Map<String, String> h = AwsSigV4.sign(SES, "application/json", BODY, "us-east-1", "ses", AKID, SECRET, "SESSIONTOKEN", T);
        assertThat(h.get("Authorization")).isEqualTo("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261028/us-east-1/ses/aws4_request, "
                + "SignedHeaders=content-type;host;x-amz-date;x-amz-security-token, "
                + "Signature=02b2fc028d2cf8dbb88d4c56993a495498d9f408040085b5c7a1f2415e5e69dc");
        assertThat(h.get("X-Amz-Security-Token")).isEqualTo("SESSIONTOKEN");
        assertThat(AwsSigV4.sign(SES, "application/json", BODY, "us-east-1", "ses", AKID, SECRET, " ", T))
                .doesNotContainKey("X-Amz-Security-Token");                   // blank token: permanent keys
    }

    @Test
    void anythingThatChangesChangesTheSignature() {
        String base = AwsSigV4.sign(SES, "application/json", BODY, "us-east-1", "ses", AKID, SECRET, null, T).get("Authorization");
        assertThat(AwsSigV4.sign(SES, "application/json", BODY + " ", "us-east-1", "ses", AKID, SECRET, null, T).get("Authorization")).isNotEqualTo(base);
        assertThat(AwsSigV4.sign(SES, "application/json", BODY, "eu-west-1", "ses", AKID, SECRET, null, T).get("Authorization")).isNotEqualTo(base);
        assertThat(AwsSigV4.sign(SES, "application/json", BODY, "us-east-1", "ses", AKID, SECRET, null, T.plusSeconds(1)).get("Authorization")).isNotEqualTo(base);
        assertThat(AwsSigV4.sign(URI.create("http://127.0.0.1:8443/v2/email/outbound-emails"), "application/json", BODY,
                "us-east-1", "ses", AKID, SECRET, null, T).get("Authorization")).isNotEqualTo(base);   // host:port is signed
    }

    @Test
    void theBodyHashIsSha256Hex() {
        assertThat(AwsSigV4.sha256Hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
