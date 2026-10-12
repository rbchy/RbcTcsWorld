package com.rbctcsworld.ecommerce.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Failed logins counted in this JVM. Default for development and tests, and the fallback while Redis is
 * unreachable. Limits: every backend instance counts on its own, and a restart forgets all locks.
 */
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private static final int MAX_TRACKED = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Duration lock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public InMemoryLoginAttemptStore(Clock clock, int maxFailures, Duration window, Duration lock) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.lock = lock;
    }

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) {
    }

    @Override
    public long lockRemainingMillis(String email) {
        Attempts a = attempts.get(email);
        Instant now = clock.instant();
        if (a == null || a.lockedUntil() == null || !now.isBefore(a.lockedUntil())) {
            return 0;
        }
        return Duration.between(now, a.lockedUntil()).toMillis();
    }

    @Override
    public void recordFailure(String email) {
        Instant now = clock.instant();
        if (attempts.size() > MAX_TRACKED) evictStale(now);
        attempts.compute(email, (k, a) -> {
            if (a == null || now.isAfter(a.firstFailure().plus(window))
                    || (a.lockedUntil() != null && !now.isBefore(a.lockedUntil()))) {
                a = new Attempts(0, now, null);                 // new window (or lock has expired)
            }
            int failures = a.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lock) : null;
            return new Attempts(failures, a.firstFailure(), lockedUntil);
        });
    }

    @Override
    public void reset(String email) {
        attempts.remove(email);
    }

    @Override
    public int failures(String email) {
        Attempts a = attempts.get(email);
        return a == null ? 0 : a.failures();
    }

    int tracked() {
        return attempts.size();
    }

    private void evictStale(Instant now) {
        attempts.entrySet().removeIf(e -> {
            Attempts a = e.getValue();
            boolean windowOver = now.isAfter(a.firstFailure().plus(window));
            boolean notLocked = a.lockedUntil() == null || !now.isBefore(a.lockedUntil());
            return windowOver && notLocked;
        });
    }
}
