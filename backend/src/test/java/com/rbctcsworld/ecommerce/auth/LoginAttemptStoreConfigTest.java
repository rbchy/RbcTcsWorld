package com.rbctcsworld.ecommerce.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LoginAttemptStoreConfigTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Duration MIN15 = Duration.ofMinutes(15);

    private static LoginAttemptStore create(String type, Supplier<RedisOps> redis) {
        return LoginAttemptStoreConfig.create(type, redis, CLOCK, 5, MIN15, MIN15);
    }

    @Test
    void memoryIsTheInMemoryStoreAndNeverTouchesRedis() {
        Supplier<RedisOps> redis = () -> { throw new AssertionError("Redis must not be created"); };
        assertThat(create("memory", redis)).isInstanceOf(InMemoryLoginAttemptStore.class);
    }

    @Test
    void redisIsTheRedisStore() {
        assertThat(create(" Redis ", () -> mock(RedisOps.class))).isInstanceOf(RedisLoginAttemptStore.class);
    }

    @Test
    void aTypoStopsTheStartInsteadOfSilentlyCountingPerInstance() {
        assertThatThrownBy(() -> create("redsi", () -> mock(RedisOps.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'redsi'");
    }

    @Test
    void springBeanMethodBuildsTheConfiguredStore() {
        LoginAttemptStore store = new LoginAttemptStoreConfig()
                .loginAttemptStore(CLOCK, "memory", "redis://localhost:6379", 5, 15, 15);
        assertThat(store).isInstanceOf(InMemoryLoginAttemptStore.class);
        store.close();                                                // default close() is a no-op
    }
}
