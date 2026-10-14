package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.SharedEnvironment;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and verifies HS256 tokens. Only the e-mail (subject) is trusted from the token: the ROLE is read
 * from the database on every request (JwtFilter), so editing the token to "role: ADMIN" gives nothing.
 *
 * Verification rejects: a wrong/edited signature, "alg: none" (unsigned) tokens, expired tokens and
 * tokens from another issuer.
 */
@Service
public class JwtService {

    public static final String ISSUER = "rbctcsworld-ecommerce";
    /** The development default in application.yml. It is public (in Git), so it must never run in production. */
    static final String DEV_DEFAULT_SECRET = "change-this-development-secret-key-which-must-be-long-enough-123456789";

    private final String secret;
    private final long expirationMs;
    private final Environment env;
    private SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs,
                      Environment env) {
        this.secret = secret;
        this.expirationMs = expirationMs;
        this.env = env;
        validateSecret();
    }

    /** Fails the application start instead of running with an unsafe key. */
    private void validateSecret() {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        if (SharedEnvironment.isShared(env) && DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException("Refusing to start: the public development JWT secret is used with the 'prod' profile"
                    + " or the public demo."
                    + " Set the JWT_SECRET environment variable.");
        }
        key = Keys.hmacShaKeyFor(bytes);
    }

    public String generate(String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Lifetime of a new access token, sent to the client as expiresIn. */
    public long expiresInSeconds() {
        return expirationMs / 1000;
    }

    /** Returns the e-mail of a valid token; throws for anything else (JwtFilter then treats the caller as anonymous). */
    public String email(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
