package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Long-lived refresh tokens with rotation and reuse detection (OAuth 2.0 Security BCP).
 *
 *  - The token is 256 random bits (not a JWT): nothing to decode, nothing to forge.
 *  - Only its SHA-256 hash is stored.
 *  - Every refresh uses the token up and returns a new one (rotation). The tokens of one login form a family.
 *  - A used token that comes back was copied: the thief or the real user is replaying it. Both get logged
 *    out by revoking the whole family; the real user simply logs in again.
 *  - Logout revokes the family, so the refresh token is worthless afterwards (the access token still lives
 *    until it expires, at most 15 minutes - the reason it is kept short).
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final UserRepository users;
    private final Clock clock;
    private final Duration lifetime;

    public RefreshTokenService(RefreshTokenRepository repo, UserRepository users, Clock clock,
                               @Value("${app.jwt.refresh-days:14}") long refreshDays) {
        this.repo = repo;
        this.users = users;
        this.clock = clock;
        this.lifetime = Duration.ofDays(refreshDays);
    }

    public record Rotated(AppUser user, String refreshToken) {
    }

    /** New login: a new family. Expired tokens of the user are cleaned up on the way. */
    @Transactional
    public String issue(AppUser user) {
        LocalDateTime now = LocalDateTime.now(clock);
        repo.deleteExpired(user.getId(), now);
        return create(user.getId(), UUID.randomUUID().toString(), now);
    }

    /**
     * Uses the token up and returns its successor. noRollbackFor: the family revocation on reuse must be
     * committed even though the call ends with 401.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotated rotate(String rawToken) {
        RefreshToken token = find(rawToken);
        LocalDateTime now = LocalDateTime.now(clock);
        if (token.getRevokedAt() != null) {
            repo.revokeFamily(token.getFamilyId(), now);             // replay of a used token
            throw new InvalidRefreshTokenException();
        }
        if (!now.isBefore(token.getExpiresAt())) {
            throw new InvalidRefreshTokenException();
        }
        if (repo.revokeIfActive(token.getId(), now) == 0) {
            repo.revokeFamily(token.getFamilyId(), now);             // lost a race against the same token
            throw new InvalidRefreshTokenException();
        }
        AppUser user = users.findById(token.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return new Rotated(user, create(token.getUserId(), token.getFamilyId(), now));
    }

    /** Logout. Unknown or empty tokens are ignored: logging out twice is not an error. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repo.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repo.revokeFamily(t.getFamilyId(), LocalDateTime.now(clock)));
    }

    public long lifetimeSeconds() {
        return lifetime.toSeconds();
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new InvalidRefreshTokenException();
        return repo.findByTokenHash(hash(rawToken)).orElseThrow(InvalidRefreshTokenException::new);
    }

    private String create(Long userId, String familyId, LocalDateTime now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repo.save(new RefreshToken(userId, hash(raw), familyId, now, now.plus(lifetime)));
        return raw;
    }

    static String hash(String rawToken) {
        return Hashes.sha256Hex(rawToken);
    }
}
