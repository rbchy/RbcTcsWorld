package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.ForbiddenException;
import com.rbctcsworld.ecommerce.common.exception.InvalidCredentialsException;
import com.rbctcsworld.ecommerce.common.exception.InvalidRefreshTokenException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public static final String DISABLED = "This account is disabled. Please contact the store.";

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptService attempts;
    private final RefreshTokenService refreshTokens;
    private volatile String dummyHash;

    public AuthService(UserRepository repo, PasswordEncoder encoder, JwtService jwt, LoginAttemptService attempts,
                       RefreshTokenService refreshTokens) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.refreshTokens = refreshTokens;
    }

    /** New accounts are always CUSTOMER; admins are only created by DataSeeder. */
    public AuthResult register(String rawEmail, String password) {
        String email = normalize(rawEmail);
        if (repo.findByEmail(email).isPresent()) {
            throw new ConflictException("Email already registered");
        }
        AppUser user = repo.save(new AppUser(email, encoder.encode(password)));
        return result(user, refreshTokens.issue(user));
    }

    public static final String GUEST_AGAIN = "You already checked out as a guest with this e-mail. "
            + "Use \"Forgot password\" to set a password and see your orders.";
    public static final String HAS_ACCOUNT = "This e-mail has an account. Please log in.";

    /**
     * Guest checkout: a customer account with a random password nobody knows. The guest can shop, pay and follow
     * the order in this browser (the session works like any other); "Forgot password" turns it into a full
     * account. An e-mail that already has an account - full or guest - is refused (409), so nobody can open
     * somebody else's orders by typing their e-mail.
     */
    public AuthResult guest(String rawEmail) {
        String email = normalize(rawEmail);
        AppUser existing = repo.findByEmail(email).orElse(null);
        if (existing != null) {
            throw new ConflictException(existing.isGuest() ? GUEST_AGAIN : HAS_ACCOUNT);
        }
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        AppUser user = repo.save(AppUser.guest(email, encoder.encode(java.util.Base64.getEncoder().encodeToString(secret))));
        return result(user, refreshTokens.issue(user));
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /**
     * Attackers must not be able to discover which e-mails have accounts:
     *  - same 401 message for an unknown e-mail and a wrong password;
     *  - same TIME: for an unknown e-mail a bcrypt check still runs (against a dummy hash). Without it
     *    an unknown e-mail answered in ~1 ms and a real one in ~90 ms - measurable from outside.
     * Brute force: after 5 failures the e-mail is locked for 15 minutes (429), see LoginAttemptService.
     */
    public AuthResult login(String rawEmail, String password) {
        String email = normalize(rawEmail);
        attempts.checkAllowed(email);
        AppUser user = repo.findByEmail(email).orElse(null);
        boolean ok;
        if (user != null) {
            ok = encoder.matches(password, user.getPassword());
        } else {
            encoder.matches(password, dummyHash());   // result ignored: only spends the same time as a real check
            ok = false;
        }
        if (!ok) {
            attempts.loginFailed(email);
            throw new InvalidCredentialsException();
        }
        attempts.loginSucceeded(email);
        if (!user.isActive()) {
            // only told after the right password, so it does not reveal which e-mails have accounts
            throw new ForbiddenException(DISABLED);
        }
        return result(user, refreshTokens.issue(user));
    }

    /** New access token + the next refresh token (the used one is spent). 401 for anything not valid. */
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshToken);
        if (!rotated.user().isActive()) {
            throw new InvalidRefreshTokenException();
        }
        return result(rotated.user(), rotated.refreshToken());
    }

    /** Ends the session on the server: the refresh token (and its whole family) stops working. */
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private AuthResult result(AppUser user, String refreshToken) {
        return new AuthResult(jwt.generate(user.getEmail()), user.getEmail(), user.getRole(), refreshToken,
                jwt.expiresInSeconds(), user.isGuest());
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = encoder.encode("timing-equalizer-" + System.nanoTime());
        }
        return dummyHash;
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * token = access token (JWT, short); refreshToken = opaque, long; expiresIn = access token lifetime in seconds;
     * guest = a guest checkout account (no password of its own yet).
     */
    public record AuthResult(String token, String email, String role, String refreshToken, long expiresIn, boolean guest) {
    }
}
