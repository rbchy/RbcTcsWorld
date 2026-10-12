package com.rbctcsworld.ecommerce.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * app.security.login.store = memory (default) | redis. Any other value stops the start: a typo must not
 * silently fall back to per-instance counting.
 */
@Configuration
class LoginAttemptStoreConfig {

    @Bean(destroyMethod = "close")
    LoginAttemptStore loginAttemptStore(Clock clock,
                                        @Value("${app.security.login.store:memory}") String type,
                                        @Value("${app.security.login.redis-url:redis://localhost:6379}") String redisUrl,
                                        @Value("${app.security.login.max-failures:5}") int maxFailures,
                                        @Value("${app.security.login.window-minutes:15}") long windowMinutes,
                                        @Value("${app.security.login.lock-minutes:15}") long lockMinutes) {
        return create(type, () -> new LettuceRedisOps(redisUrl), clock, maxFailures,
                Duration.ofMinutes(windowMinutes), Duration.ofMinutes(lockMinutes));
    }

    static LoginAttemptStore create(String type, java.util.function.Supplier<RedisOps> redis, Clock clock,
                                    int maxFailures, Duration window, Duration lock) {
        InMemoryLoginAttemptStore memory = new InMemoryLoginAttemptStore(clock, maxFailures, window, lock);
        return switch (type.trim().toLowerCase()) {
            case "memory" -> memory;
            case "redis" -> new RedisLoginAttemptStore(redis.get(), memory, clock, maxFailures, window, lock);
            default -> throw new IllegalStateException(
                    "app.security.login.store must be 'memory' or 'redis', not '" + type + "'");
        };
    }
}
