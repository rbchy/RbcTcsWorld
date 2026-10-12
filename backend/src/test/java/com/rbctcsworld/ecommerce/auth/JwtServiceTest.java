package com.rbctcsworld.ecommerce.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Attacks on the token itself. Every one of them must be rejected by JwtService.email(). */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-which-is-definitely-longer-than-32-bytes";
    private final JwtService jwt = new JwtService(SECRET, 60_000, new MockEnvironment());

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validTokenRoundTrip() {
        assertThat(jwt.email(jwt.generate("a@test.com"))).isEqualTo("a@test.com");
    }

    @Test
    void editedPayloadBreaksTheSignature() {
        String[] parts = jwt.generate("customer@test.com").split("\\.");
        String forged = parts[0] + "." + b64("{\"iss\":\"rbctcsworld-ecommerce\",\"sub\":\"admin@rbctcsworld.com\"}") + "." + parts[2];
        assertThatThrownBy(() -> jwt.email(forged)).isInstanceOf(Exception.class);
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        String none = b64("{\"alg\":\"none\"}") + "." + b64("{\"iss\":\"rbctcsworld-ecommerce\",\"sub\":\"admin@rbctcsworld.com\"}") + ".";
        assertThatThrownBy(() -> jwt.email(none)).isInstanceOf(Exception.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String other = Jwts.builder().issuer(JwtService.ISSUER).subject("admin@rbctcsworld.com")
                .signWith(Keys.hmacShaKeyFor("attacker-secret-attacker-secret-attacker-secret".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> jwt.email(other)).isInstanceOf(Exception.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder().issuer(JwtService.ISSUER).subject("a@test.com")
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThatThrownBy(() -> jwt.email(expired)).isInstanceOf(Exception.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = Jwts.builder().issuer("some-other-app").subject("a@test.com")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThatThrownBy(() -> jwt.email(foreign)).isInstanceOf(Exception.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> jwt.email("not.a.token")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jwt.email("")).isInstanceOf(Exception.class);
    }

    @Test
    void secretShorterThan256BitsStopsTheApplication() {
        assertThatThrownBy(() -> new JwtService("too-short", 60_000, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void publicDevSecretIsRefusedInProduction() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtService(JwtService.DEV_DEFAULT_SECRET, 60_000, prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatCode(() -> new JwtService(SECRET, 60_000, prod)).doesNotThrowAnyException();
        assertThatCode(() -> new JwtService(JwtService.DEV_DEFAULT_SECRET, 60_000, new MockEnvironment()))
                .as("allowed for local development").doesNotThrowAnyException();
    }

    @Test
    void secretOfExactly32BytesIsTheMinimum() {  // added after PIT: the "< 32" boundary and the null check survived
        assertThatCode(() -> new JwtService("x".repeat(32), 60_000, new MockEnvironment())).doesNotThrowAnyException();
        assertThatThrownBy(() -> new JwtService("x".repeat(31), 60_000, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new JwtService(null, 60_000, new MockEnvironment()))
                .as("missing secret: a clear start-up error, not a NullPointerException")
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void expiresInIsTheLifetimeInSeconds() {
        org.assertj.core.api.Assertions.assertThat(jwt.expiresInSeconds()).isEqualTo(60L);
        org.assertj.core.api.Assertions.assertThat(new JwtService(SECRET, 900_000, new MockEnvironment()).expiresInSeconds())
                .isEqualTo(900L);
    }
}
