package com.rbctcsworld.ecommerce.auth;

/**
 * Where failed logins are counted. In memory (one backend) or in Redis (several backends share one count,
 * so an attacker cannot spread guesses over instances and a restart does not unlock an account).
 */
public interface LoginAttemptStore extends AutoCloseable {

    /** Milliseconds this e-mail stays locked; 0 when it is not locked. */
    long lockRemainingMillis(String email);

    /** Counts a failure; the MAX-th failure within the window locks the e-mail. */
    void recordFailure(String email);

    /** Successful login: the counter starts from zero. */
    void reset(String email);

    /** Current number of counted failures (tests and diagnostics). */
    int failures(String email);

    @Override
    default void close() {
    }
}
