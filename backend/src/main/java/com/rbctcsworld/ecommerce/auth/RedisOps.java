package com.rbctcsworld.ecommerce.auth;

/** The few Redis operations the login lockout needs. Small on purpose: easy to fake in unit tests. */
interface RedisOps extends AutoCloseable {

    /**
     * Atomically: count one failure (the counter expires windowMillis after the first one) and, at
     * maxFailures, set the lock key for lockMillis and clear the counter. Returns the new count.
     */
    long recordFailure(String failKey, String lockKey, long windowMillis, int maxFailures, long lockMillis);

    /** Remaining lifetime of the key in ms; negative when the key does not exist. */
    long ttlMillis(String key);

    /** Integer value of the key, 0 when absent. */
    long count(String key);

    void delete(String... keys);

    @Override
    default void close() {
    }
}
