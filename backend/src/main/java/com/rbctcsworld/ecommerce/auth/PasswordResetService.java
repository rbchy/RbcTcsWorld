package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.notification.AccountEmailEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * "Forgot password" by e-mail.
 *
 * Request (POST /api/auth/forgot-password):
 *  - the answer is the same whether or not the e-mail has an account, so nobody can find out who is a customer;
 *  - a link is a random 256-bit token; only its SHA-256 hash is stored;
 *  - at most MAX_PER_HOUR links per account and hour (silently: the answer stays the same);
 *  - a new link makes older open links unusable.
 * Reset (POST /api/auth/reset-password):
 *  - the link works once and for LIFETIME; an unknown, used or expired link gets the same 400;
 *  - the new password follows the registration rule (8-72 characters);
 *  - every session ends (refresh tokens revoked), a login lockout is lifted, and the customer gets a
 *    "your password was changed" e-mail - if it was not them, they know at once.
 * The e-mails go through the outbox (same transaction), and the delivered reset e-mail is redacted.
 */
@Service
public class PasswordResetService {

    static final Duration LIFETIME = Duration.ofMinutes(30);
    static final int MAX_PER_HOUR = 3;
    static final String INVALID_LINK = "This reset link is invalid or has expired. Please ask for a new one.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final LoginAttemptService attempts;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String publicUrl;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                                LoginAttemptService attempts, ApplicationEventPublisher events, Clock clock,
                                @Value("${app.public-url:http://localhost:5173}") String publicUrl) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.attempts = attempts;
        this.events = events;
        this.clock = clock;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** Sends a reset link if the account exists and the hourly limit allows it. Never says which happened. */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1)) >= MAX_PER_HOUR) {
            return;
        }
        tokens.useAllOf(user.getId(), now);
        String raw = newToken();
        tokens.save(new PasswordResetToken(user.getId(), RefreshTokenService.hash(raw), now, now.plus(LIFETIME)));
        events.publishEvent(new AccountEmailEvent(user.getEmail(), AccountEmailEvent.Type.PASSWORD_RESET,
                "Reset your RbcTcsWorld password",
                "Someone asked to reset the password of your RbcTcsWorld account.\n\n"
                        + "Choose a new password here (the link works once, for 30 minutes):\n"
                        + publicUrl + "/#/reset-password?token=" + raw + "\n\n"
                        + "Did not ask for this? Ignore this e-mail - your password stays the same."));
    }

    /** Sets the new password with a valid link; 400 INVALID_LINK otherwise. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = rawToken == null || rawToken.isBlank() ? null
                : tokens.findByTokenHash(RefreshTokenService.hash(rawToken)).orElse(null);
        if (token == null || token.getUsedAt() != null || !now.isBefore(token.getExpiresAt())
                || tokens.useIfUnused(token.getId(), now) == 0) {
            throw new BusinessRuleException(INVALID_LINK);
        }
        AppUser user = users.findById(token.getUserId()).orElseThrow(() -> new BusinessRuleException(INVALID_LINK));
        user.changePassword(encoder.encode(newPassword));
        user.claim();                                   // a guest who sets a password now has a full account
        users.save(user);
        tokens.useAllOf(user.getId(), now);
        refreshTokens.revokeAllOf(user.getId(), now);
        attempts.loginSucceeded(user.getEmail());
        events.publishEvent(new AccountEmailEvent(user.getEmail(), AccountEmailEvent.Type.PASSWORD_CHANGED,
                "Your RbcTcsWorld password was changed",
                "The password of your RbcTcsWorld account was just changed, and every device was signed out.\n\n"
                        + "Was this not you? Ask for a new reset link on the login page right away and reply to this e-mail."));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
