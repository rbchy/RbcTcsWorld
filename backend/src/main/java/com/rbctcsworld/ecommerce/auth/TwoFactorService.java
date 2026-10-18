package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.InvalidCredentialsException;
import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import com.rbctcsworld.ecommerce.notification.AccountEmailEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Two-factor sign-in by e-mail code (opt-in, REQ-AUTH-16).
 *
 *  - After the right password, a 6-digit code goes to the account's e-mail and the browser gets a random
 *    challenge id; both are needed to finish the sign-in.
 *  - Only a SHA-256 hash of challenge id + code is stored; the delivered e-mail body is redacted.
 *  - A challenge works once, for 10 minutes and for 5 wrong guesses (counted in the database); an unknown, used,
 *    expired or exhausted challenge all get the same 400.
 *  - At most 5 codes per account and hour (429), and a new code ends older open ones.
 */
@Service
public class TwoFactorService {

    static final Duration LIFETIME = Duration.ofMinutes(10);
    static final int MAX_GUESSES = 5;
    static final int MAX_CODES_PER_HOUR = 5;
    static final String INVALID = "This sign-in code is wrong or has expired. Please sign in again.";
    static final String GUEST = "Set a password first (use \"Forgot password\"), then turn on two-factor sign-in.";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** What the browser keeps between the two steps. */
    public record Challenge(boolean twoFactorRequired, String challengeToken, String message) {
    }

    private final UserRepository users;
    private final LoginChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TwoFactorService(UserRepository users, LoginChallengeRepository challenges, PasswordEncoder encoder,
                            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.challenges = challenges;
        this.encoder = encoder;
        this.events = events;
        this.clock = clock;
    }

    /** Sends a new code and returns the challenge id. 429 after too many codes in an hour. */
    @Transactional
    public Challenge begin(AppUser user) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (challenges.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1)) >= MAX_CODES_PER_HOUR) {
            throw new TooManyRequestsException("Too many sign-in codes. Please try again later.", 3600);
        }
        challenges.useAllOf(user.getId(), now);
        String challengeToken = randomToken();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        challenges.save(new LoginChallenge(user.getId(), RefreshTokenService.hash(challengeToken),
                codeHash(challengeToken, code), now, now.plus(LIFETIME)));
        events.publishEvent(new AccountEmailEvent(user.getEmail(), AccountEmailEvent.Type.LOGIN_CODE,
                "Your RbcTcsWorld sign-in code",
                "Your sign-in code is " + code + "\n\nIt works once, for 10 minutes.\n\n"
                        + "Did not try to sign in? Someone knows your password - change it with \"Forgot password\"."));
        return new Challenge(true, challengeToken, "A 6-digit code was sent to your e-mail.");
    }

    /** Checks the code; returns the account to sign in. 400 INVALID for every kind of failure. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AppUser verify(String challengeToken, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        LoginChallenge c = challengeToken == null || challengeToken.isBlank() ? null
                : challenges.findByChallengeHash(RefreshTokenService.hash(challengeToken)).orElse(null);
        if (c == null || c.getUsedAt() != null || !now.isBefore(c.getExpiresAt()) || c.getAttempts() >= MAX_GUESSES) {
            throw new BusinessRuleException(INVALID);
        }
        boolean right = code != null && MessageDigest.isEqual(
                codeHash(challengeToken, code.trim()).getBytes(StandardCharsets.UTF_8), c.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!right) {
            challenges.countAttempt(c.getId());
            throw new BusinessRuleException(INVALID);
        }
        if (challenges.useIfUnused(c.getId(), now) == 0) {
            throw new BusinessRuleException(INVALID);
        }
        return users.findById(c.getUserId()).orElseThrow(() -> new BusinessRuleException(INVALID));
    }

    /** Turns two-factor sign-in on or off. Needs the current password; a guest has none yet. */
    @Transactional
    public boolean setEnabled(String email, boolean enabled, String password) {
        AppUser user = users.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);
        if (user.isGuest()) {
            throw new BusinessRuleException(GUEST);
        }
        if (!encoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (user.isTwoFactorEnabled() != enabled) {
            user.setTwoFactorEnabled(enabled);
            users.save(user);
            events.publishEvent(new AccountEmailEvent(user.getEmail(), AccountEmailEvent.Type.TWO_FACTOR_CHANGED,
                    "Two-factor sign-in turned " + (enabled ? "on" : "off"),
                    "Two-factor sign-in was just turned " + (enabled ? "on" : "off") + " for your RbcTcsWorld account.\n\n"
                            + "Was this not you? Change your password with \"Forgot password\" right away."));
        }
        return enabled;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String email) {
        return users.findByEmail(email == null ? "" : email.trim().toLowerCase()).map(AppUser::isTwoFactorEnabled).orElse(false);
    }

    /** Hash of challenge id + code: the database alone cannot be used to find the 6 digits. */
    static String codeHash(String challengeToken, String code) {
        return RefreshTokenService.hash(challengeToken + ":" + code);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
