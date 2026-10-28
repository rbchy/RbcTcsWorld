package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only messages signed by Amazon SNS, with a certificate from SNS itself, are accepted. */
class SnsMessageVerifierTest {

    private final AtomicInteger downloads = new AtomicInteger();
    private final SnsMessageVerifier verifier = new SnsMessageVerifier(u -> {
        downloads.incrementAndGet();
        return SnsTestSigner.download(u);
    });

    private static JsonNode fixture(String name) {
        return SnsTestSigner.json(SnsTestSigner.fixture(name));
    }

    private static JsonNode with(String json, String field, String value) {
        return SnsTestSigner.json(json.replaceFirst("\"" + field + "\"\\s*:\\s*\"[^\"]*\"",
                java.util.regex.Matcher.quoteReplacement("\"" + field + "\": \"" + value + "\"")));
    }

    @Test
    void messagesSignedOutsideJavaAreAcceptedForBothSignatureVersions() {
        assertThatCode(() -> verifier.verify(fixture("sns_bounce.json"))).doesNotThrowAnyException();          // SHA256withRSA
        assertThatCode(() -> verifier.verify(fixture("sns_complaint_v1.json"))).doesNotThrowAnyException();    // SHA1withRSA, with Subject
        assertThatCode(() -> verifier.verify(fixture("sns_subscription.json"))).doesNotThrowAnyException();    // SubscribeURL + Token
        assertThat(downloads).hasValue(1);                                     // the certificate is fetched once
    }

    @Test
    void anyChangedFieldBreaksTheSignature() {
        String bounce = SnsTestSigner.fixture("sns_bounce.json");
        for (String[] change : new String[][] {{"MessageId", "msg-bounce-2"}, {"Timestamp", "2026-10-28T04:00:00.000Z"},
                {"Type", "SubscriptionConfirmation"}, {"TopicArn", "arn:aws:sns:us-east-1:999999999999:other"}}) {
            assertThatThrownBy(() -> verifier.verify(with(bounce, change[0], change[1])))
                    .as(change[0]).isInstanceOf(BusinessRuleException.class).hasMessage(SnsMessageVerifier.INVALID);
        }
        assertThatThrownBy(() -> verifier.verify(SnsTestSigner.json(bounce.replace("Gone@Example.com", "Other@Example.com"))))
                .isInstanceOf(BusinessRuleException.class);
        String complaint = SnsTestSigner.fixture("sns_complaint_v1.json");
        assertThatThrownBy(() -> verifier.verify(with(complaint, "Subject", "Hello"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> verifier.verify(with(complaint, "SignatureVersion", "2"))).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://sns.us-east-1.amazonaws.com/cert.pem", "https://evil.example.com/cert.pem",
            "https://sns.us-east-1.amazonaws.com.evil.com/cert.pem", "https://sns.us-east-1.amazonaws.com:8443/cert.pem",
            "https://user@sns.us-east-1.amazonaws.com/cert.pem", "https://sns.us-east-1.amazonaws.com/cert.txt",
            "https://s3.amazonaws.com/cert.pem", "not a url", ""})
    void theCertificateMustComeFromSnsItself(String url) {
        JsonNode m = with(SnsTestSigner.fixture("sns_bounce.json"), "SigningCertURL", url);
        assertThatThrownBy(() -> verifier.verify(m)).isInstanceOf(BusinessRuleException.class);
        assertThat(downloads).hasValue(0);                                     // never even downloaded
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "0", ""})
    void anUnknownSignatureVersionIsRefused(String version) {
        JsonNode m = with(SnsTestSigner.fixture("sns_bounce.json"), "SignatureVersion", version);
        assertThatThrownBy(() -> verifier.verify(m)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aMissingOrMalformedSignatureIsRefused() {
        String bounce = SnsTestSigner.fixture("sns_bounce.json");
        assertThatThrownBy(() -> verifier.verify(with(bounce, "Signature", "not base64 !!"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> verifier.verify(with(bounce, "Signature", "AAAA"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> verifier.verify(SnsTestSigner.json(bounce.replaceFirst("\"Signature\"", "\"Sig\""))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> verifier.verify(SnsTestSigner.json(bounce.replaceFirst("\"Type\"", "\"Kind\""))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> verifier.verify(SnsTestSigner.json("{}"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aCertificateThatIsNotACertificateIsRefused() {
        SnsMessageVerifier broken = new SnsMessageVerifier(u -> "not a certificate".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> broken.verify(fixture("sns_bounce.json"))).isInstanceOf(BusinessRuleException.class)
                .hasMessage(SnsMessageVerifier.INVALID);
    }

    @Test
    void aMessageSignedWithAnotherKeyIsRefused() {
        // the right certificate URL, but the certificate served there is not the one that signed the message
        SnsMessageVerifier other = new SnsMessageVerifier(u -> otherCertificate());
        assertThatThrownBy(() -> other.verify(fixture("sns_bounce.json"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void snsUrlsAreHttpsOnAnSnsHost() {
        assertThat(SnsMessageVerifier.snsUrl("https://sns.eu-west-1.amazonaws.com/?Action=ConfirmSubscription"))
                .isEqualTo(URI.create("https://sns.eu-west-1.amazonaws.com/?Action=ConfirmSubscription"));
        assertThat(SnsMessageVerifier.snsUrl("https://sns.cn-north-1.amazonaws.com.cn/x.pem").getHost()).isEqualTo("sns.cn-north-1.amazonaws.com.cn");
    }

    @ParameterizedTest
    @NullSource
    void noUrlIsRefused(String url) {
        assertThatThrownBy(() -> SnsMessageVerifier.snsUrl(url)).isInstanceOf(BusinessRuleException.class);
    }

    /** A real but different certificate: the JDK's own cacerts have plenty; take the first one. */
    private static byte[] otherCertificate() {
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(System.getProperty("java.home"), "lib", "security", "cacerts"))) {
                ks.load(in, null);
            }
            String alias = ks.aliases().nextElement();
            return ("-----BEGIN CERTIFICATE-----\n" + java.util.Base64.getMimeEncoder().encodeToString(ks.getCertificate(alias).getEncoded())
                    + "\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
