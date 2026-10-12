package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * Brute-force protection for the login endpoint.
 *
 *  - After MAX failed logins for one e-mail within the WINDOW, that e-mail is locked for LOCK time:
 *    every login (even with the right password) gets 429 + Retry-After, so guessing stops.
 *  - Unknown e-mails are counted exactly like real ones, so the 429 does not reveal which accounts exist.
 *  - A successful login clears the counter.
 *  - The counting lives in a LoginAttemptStore: Redis when app.security.login.store=redis (shared by all
 *    backend instances, survives restarts), otherwise in memory. See LoginAttemptStoreConfig.
 *
 * Trade-off (documented): someone who knows a customer's e-mail can lock that account for 15 minutes.
 * A temporary lock (not a permanent one) keeps that risk small.
 */
@Service
public class LoginAttemptService {

    private final LoginAttemptStore store;

    @Autowired
    public LoginAttemptService(LoginAttemptStore store) {
        this.store = store;
    }

    /** In-memory variant for unit tests. */
    LoginAttemptService(Clock clock, int maxFailures, long windowMinutes, long lockMinutes) {
        this(new InMemoryLoginAttemptStore(clock, maxFailures, Duration.ofMinutes(windowMinutes),
                Duration.ofMinutes(lockMinutes)));
    }

    /** Throws 429 while the e-mail is locked. Call BEFORE checking the password. */
    public void checkAllowed(String email) {
        long millis = store.lockRemainingMillis(email);
        if (millis > 0) {
            // round UP: a client that waits exactly Retry-After seconds must not still be locked
            long seconds = Math.max(1, (millis + 999) / 1000);
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again in " + seconds + " seconds.", seconds);
        }
    }

    public void loginFailed(String email) {
        store.recordFailure(email);
    }

    public void loginSucceeded(String email) {
        store.reset(email);
    }

    int failures(String email) {
        return store.failures(email);
    }
}
