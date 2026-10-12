package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.auth.LoginAttemptStore;
import com.rbctcsworld.ecommerce.auth.RedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The lockout against a REAL Redis (Testcontainers): the Lua script, key expiry, and the reason Redis is
 * used at all - two backend instances see the same lock. Skipped when Docker is not available.
 */
class RedisLoginLockoutIntegrationTest {

    private static GenericContainer<?> redis;
    private static String url;
    private final List<LoginAttemptStore> opened = new ArrayList<>();

    @BeforeAll
    static void startRedis() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
        url = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
    }

    @AfterAll
    static void stopRedis() {
        if (redis != null) redis.stop();
    }

    @AfterEach
    void close() {
        opened.forEach(LoginAttemptStore::close);
    }

    /** One "backend instance": its own Redis connection and its own in-memory fallback. */
    private LoginAttemptStore instance(String redisUrl, Duration window, Duration lock) {
        LoginAttemptStore s = RedisTestSupport.redisStore(redisUrl, 5, window, lock);
        opened.add(s);
        return s;
    }

    private static String email() {
        return "redis-" + UUID.randomUUID() + "@test.com";
    }

    @Test
    void fifthFailureLocksForTheLockTime() {
        LoginAttemptStore store = instance(url, Duration.ofMinutes(15), Duration.ofMinutes(15));
        String email = email();
        for (int i = 0; i < 4; i++) store.recordFailure(email);
        assertThat(store.failures(email)).isEqualTo(4);
        assertThat(store.lockRemainingMillis(email)).isZero();

        store.recordFailure(email);

        assertThat(store.lockRemainingMillis(email)).isBetween(899_000L, 900_000L);
        assertThat(store.failures(email)).isZero();                  // counter cleared when the lock is set
    }

    @Test
    void lockIsSharedByAllBackendInstances() {
        LoginAttemptStore backendA = instance(url, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptStore backendB = instance(url, Duration.ofMinutes(15), Duration.ofMinutes(15));
        String email = email();

        for (int i = 0; i < 3; i++) backendA.recordFailure(email);   // attacker spreads guesses
        for (int i = 0; i < 2; i++) backendB.recordFailure(email);   // over two instances

        assertThat(backendA.lockRemainingMillis(email)).isPositive();
        assertThat(backendB.lockRemainingMillis(email)).isPositive();
    }

    @Test
    void successOnOneInstanceResetsTheCounterForAll() {
        LoginAttemptStore backendA = instance(url, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptStore backendB = instance(url, Duration.ofMinutes(15), Duration.ofMinutes(15));
        String email = email();
        for (int i = 0; i < 4; i++) backendA.recordFailure(email);

        backendB.reset(email);

        assertThat(backendA.failures(email)).isZero();
    }

    @Test
    void redisExpiresTheWindowAndTheLockByItself() throws InterruptedException {
        LoginAttemptStore store = instance(url, Duration.ofMillis(300), Duration.ofMillis(300));
        String slow = email();
        for (int i = 0; i < 4; i++) store.recordFailure(slow);
        Thread.sleep(600);                                           // window over: counting starts again
        assertThat(store.failures(slow)).isZero();

        String locked = email();
        for (int i = 0; i < 5; i++) store.recordFailure(locked);
        assertThat(store.lockRemainingMillis(locked)).isPositive();
        Thread.sleep(600);
        assertThat(store.lockRemainingMillis(locked)).isZero();
    }

    @Test
    void unreachableRedisFallsBackToMemoryAndStillLocks() {
        LoginAttemptStore store = instance("redis://localhost:1", Duration.ofMinutes(15), Duration.ofMinutes(15));
        String email = email();
        for (int i = 0; i < 5; i++) store.recordFailure(email);
        assertThat(store.lockRemainingMillis(email)).isPositive();
        assertThat(store.failures(email)).isEqualTo(5);
    }
}
