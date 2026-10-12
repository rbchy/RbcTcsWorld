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
 *  - Grace period (as Okta/Auth0): a response can get lost - the page reloads or the tab closes while the
 *    refresh is on the way, and the browser still holds the spent token. Within GRACE seconds the spent
 *    token is accepted once more, but only while its successor is still unused and the session is alive.
 *    Two tabs refreshing at the same moment are covered by the same rule.
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
    private final Duration grace;

    public RefreshTokenService(RefreshTokenRepository repo, UserRepository users, Clock clock,
                               @Value("${app.jwt.refresh-days:14}") long refreshDays,
                               @Value("${app.jwt.refresh-grace-seconds:30}") long graceSeconds) {
        this.repo = repo;
        this.users = users;
        this.clock = clock;
        this.lifetime = Duration.ofDays(refreshDays);
        this.grace = Duration.ofSeconds(graceSeconds);
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
        if (!now.isBefore(token.getExpiresAt())) {
            throw new InvalidRefreshTokenException();
        }
        if (token.getRevokedAt() == null && repo.revokeIfActive(token.getId(), now) == 1) {
            return successor(token, now);                            // the normal case
        }
        // spent - earlier, or a moment ago by a parallel request (then read its new state)
        RefreshToken spent = token.getRevokedAt() != null ? token : find(rawToken);
        if (lostResponse(spent, now)) {
            return successor(spent, now);
        }
        repo.revokeFamily(spent.getFamilyId(), now);                 // replay of a used token: end the session
        throw new InvalidRefreshTokenException();
    }

    /** Spent within the grace period, its successor unused, the session still alive. */
    private boolean lostResponse(RefreshToken spent, LocalDateTime now) {
        LocalDateTime revokedAt = spent.getRevokedAt();
        return revokedAt != null
                && now.isBefore(revokedAt.plus(grace))
                && !repo.existsByFamilyIdAndRevokedAtAfter(spent.getFamilyId(), revokedAt)
                && repo.existsByFamilyIdAndRevokedAtIsNull(spent.getFamilyId());
    }

    private Rotated successor(RefreshToken token, LocalDateTime now) {
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
