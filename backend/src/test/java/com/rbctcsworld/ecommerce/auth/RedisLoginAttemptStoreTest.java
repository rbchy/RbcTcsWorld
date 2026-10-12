package com.rbctcsworld.ecommerce.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Logic of the Redis store with a fake RedisOps; the real Redis is in RedisLoginLockoutIntegrationTest. */
@ExtendWith(MockitoExtension.class)
class RedisLoginAttemptStoreTest {

    private static final String EMAIL = "victim@test.com";
    private static final String FAIL = RedisLoginAttemptStore.failKey(EMAIL);
    private static final String LOCK = RedisLoginAttemptStore.lockKey(EMAIL);

    @Mock RedisOps redis;
    @Mock LoginAttemptStore fallback;
    private RedisLoginAttemptStore store;
    private LoginAttemptServiceTest.TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new LoginAttemptServiceTest.TestClock();
        store = new RedisLoginAttemptStore(redis, fallback, clock, 5, Duration.ofMinutes(15), Duration.ofMinutes(10));
    }

    private static IllegalStateException down() {
        return new IllegalStateException("connection refused");
    }

    @Test
    void keysContainAHashNotTheEmail() {
        assertThat(FAIL).isEqualTo("rbc:login:fail:" + Hashes.sha256Hex(EMAIL)).doesNotContain("victim");
        assertThat(LOCK).isEqualTo("rbc:login:lock:" + Hashes.sha256Hex(EMAIL)).doesNotContain("victim");
        assertThat(RedisLoginAttemptStore.failKey("other@test.com")).isNotEqualTo(FAIL);
    }

    @Test
    void lockedWhileTheLockKeyLives() {
        when(redis.ttlMillis(LOCK)).thenReturn(599_500L);
        assertThat(store.lockRemainingMillis(EMAIL)).isEqualTo(599_500L);
    }

    @Test
    void missingLockKeyMeansNotLocked() {
        when(redis.ttlMillis(LOCK)).thenReturn(-2L);
        assertThat(store.lockRemainingMillis(EMAIL)).isZero();
    }

    @Test
    void zeroTtlMeansNotLocked() {
        when(redis.ttlMillis(LOCK)).thenReturn(0L);
        assertThat(store.lockRemainingMillis(EMAIL)).isZero();
    }

    @Test
    void oneMillisecondLeftIsStillLocked() {
        when(redis.ttlMillis(LOCK)).thenReturn(1L);
        assertThat(store.lockRemainingMillis(EMAIL)).isEqualTo(1L);
    }

    @Test
    void failureIsCountedInRedisWithWindowMaxAndLockInMilliseconds() {
        store.recordFailure(EMAIL);
        verify(redis).recordFailure(FAIL, LOCK, 900_000L, 5, 600_000L);
        verifyNoInteractions(fallback);
    }

    @Test
    void successClearsTheCounterInRedisAndInTheFallback() {
        store.reset(EMAIL);
        verify(redis).delete(FAIL);
        verify(fallback).reset(EMAIL);
    }

    @Test
    void failuresAreReadFromRedis() {
        when(redis.count(FAIL)).thenReturn(3L);
        assertThat(store.failures(EMAIL)).isEqualTo(3);
    }

    // ---------- Redis down: the login keeps working with the in-memory fallback ----------

    @Test
    void redisDownLockCheckUsesTheFallback() {
        when(redis.ttlMillis(anyString())).thenThrow(down());
        when(fallback.lockRemainingMillis(EMAIL)).thenReturn(1234L);
        assertThat(store.lockRemainingMillis(EMAIL)).isEqualTo(1234L);
        assertThat(store.outages()).isEqualTo(1);
    }

    @Test
    void redisDownFailureIsCountedInTheFallback() {
        when(redis.recordFailure(anyString(), anyString(), anyLong(), anyInt(), anyLong())).thenThrow(down());
        store.recordFailure(EMAIL);
        verify(fallback).recordFailure(EMAIL);
        assertThat(store.outages()).isEqualTo(1);
    }

    @Test
    void redisDownResetStillClearsTheFallback() {
        doThrow(down()).when(redis).delete(FAIL);
        store.reset(EMAIL);
        verify(fallback).reset(EMAIL);
        assertThat(store.outages()).isEqualTo(1);
    }

    @Test
    void redisDownFailuresComeFromTheFallback() {
        when(redis.count(anyString())).thenThrow(down());
        when(fallback.failures(EMAIL)).thenReturn(2);
        assertThat(store.failures(EMAIL)).isEqualTo(2);
        assertThat(store.outages()).isEqualTo(1);
    }

    @Test
    void healthyRedisIsNoOutage() {
        when(redis.ttlMillis(LOCK)).thenReturn(-2L);
        store.lockRemainingMillis(EMAIL);
        store.recordFailure(EMAIL);
        store.reset(EMAIL);
        assertThat(store.outages()).isZero();
        assertThat(store.warnings()).isZero();
    }

    @Test
    void anOutageIsLoggedOncePerMinuteNotOncePerLogin() {
        when(redis.ttlMillis(anyString())).thenThrow(down());
        when(fallback.lockRemainingMillis(EMAIL)).thenReturn(0L);

        for (int i = 0; i < 3; i++) assertThat(store.lockRemainingMillis(EMAIL)).isZero();
        assertThat(store.outages()).isEqualTo(3);
        assertThat(store.warnings()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(60));                         // exactly a minute: still quiet
        store.lockRemainingMillis(EMAIL);
        assertThat(store.warnings()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        store.lockRemainingMillis(EMAIL);
        assertThat(store.warnings()).isEqualTo(2);
    }

    @Test
    void closeClosesTheRedisConnection() {
        store.close();
        verify(redis).close();
    }
}
