package com.rbctcsworld.ecommerce.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Failed logins counted in Redis, shared by every backend instance and kept across restarts.
 *
 *  - Keys hold a SHA-256 of the e-mail, never the e-mail itself: rbc:login:fail:{hash}, rbc:login:lock:{hash}.
 *  - Redis expires the keys (window and lock time), so nothing has to be cleaned up.
 *  - Redis unreachable: the login keeps working and the in-memory store takes over (fail-open with a
 *    fallback). Choosing to keep the shop usable over a hard 503; the fallback still stops brute force on
 *    each instance. One warning per minute in the log, not one per login.
 */
class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptStore.class);
    static final String PREFIX = "rbc:login:";

    private final RedisOps redis;
    private final LoginAttemptStore fallback;
    private final Clock clock;
    private final int maxFailures;
    private final long windowMillis;
    private final long lockMillis;
    private volatile Instant lastWarning = Instant.MIN;
    private final AtomicLong outages = new AtomicLong();
    private final AtomicLong warnings = new AtomicLong();

    RedisLoginAttemptStore(RedisOps redis, LoginAttemptStore fallback, Clock clock,
                           int maxFailures, Duration window, Duration lock) {
        this.redis = redis;
        this.fallback = fallback;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
        this.lockMillis = lock.toMillis();
    }

    static String failKey(String email) {
        return PREFIX + "fail:" + Hashes.sha256Hex(email);
    }

    static String lockKey(String email) {
        return PREFIX + "lock:" + Hashes.sha256Hex(email);
    }

    @Override
    public long lockRemainingMillis(String email) {
        try {
            return Math.max(0, redis.ttlMillis(lockKey(email)));      // -2 = no lock key

        } catch (RuntimeException e) {
            unavailable(e);
            return fallback.lockRemainingMillis(email);
        }
    }

    @Override
    public void recordFailure(String email) {
        try {
            redis.recordFailure(failKey(email), lockKey(email), windowMillis, maxFailures, lockMillis);
        } catch (RuntimeException e) {
            unavailable(e);
            fallback.recordFailure(email);
        }
    }

    @Override
    public void reset(String email) {
        fallback.reset(email);
        try {
            redis.delete(failKey(email));
        } catch (RuntimeException e) {
            unavailable(e);
        }
    }

    @Override
    public int failures(String email) {
        try {
            return (int) redis.count(failKey(email));
        } catch (RuntimeException e) {
            unavailable(e);
            return fallback.failures(email);
        }
    }

    @Override
    public void close() {
        redis.close();
    }

    /** Calls answered by the in-memory fallback because Redis failed. */
    long outages() {
        return outages.get();
    }

    /** Warnings written to the log (at most one per minute). */
    long warnings() {
        return warnings.get();
    }

    private void unavailable(RuntimeException e) {
        outages.incrementAndGet();
        Instant now = clock.instant();
        if (now.isAfter(lastWarning.plusSeconds(60))) {
            lastWarning = now;
            warnings.incrementAndGet();
            log.warn("Redis is not reachable, login lockout uses the in-memory fallback: {}", e.toString());
        }
    }
}
