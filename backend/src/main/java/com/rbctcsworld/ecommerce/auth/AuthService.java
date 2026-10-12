package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

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
        return result(user, refreshTokens.issue(user));
    }

    /** New access token + the next refresh token (the used one is spent). 401 for anything not valid. */
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshToken);
        return result(rotated.user(), rotated.refreshToken());
    }

    /** Ends the session on the server: the refresh token (and its whole family) stops working. */
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private AuthResult result(AppUser user, String refreshToken) {
        return new AuthResult(jwt.generate(user.getEmail()), user.getEmail(), user.getRole(), refreshToken,
                jwt.expiresInSeconds());
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

    /** token = access token (JWT, short); refreshToken = opaque, long; expiresIn = access token lifetime in seconds. */
    public record AuthResult(String token, String email, String role, String refreshToken, long expiresIn) {
    }
}
