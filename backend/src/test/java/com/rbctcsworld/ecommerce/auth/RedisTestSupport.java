package com.rbctcsworld.ecommerce.auth;

import java.time.Clock;
import java.time.Duration;

/** Lets integration tests (other package) build the package-private Redis store exactly like the config does. */
public final class RedisTestSupport {

    private RedisTestSupport() {
    }

    public static LoginAttemptStore redisStore(String url, int maxFailures, Duration window, Duration lock) {
        return LoginAttemptStoreConfig.create("redis", () -> new LettuceRedisOps(url), Clock.systemUTC(),
                maxFailures, window, lock);
    }
}
