package com.rbctcsworld.ecommerce.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLoginAttemptStoreTest {

    private final LoginAttemptServiceTest.TestClock clock = new LoginAttemptServiceTest.TestClock();
    private final InMemoryLoginAttemptStore store =
            new InMemoryLoginAttemptStore(clock, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));

    @Test
    void lockRemainingCountsDownToZero() {
        for (int i = 0; i < 5; i++) store.recordFailure("a@test.com");
        assertThat(store.lockRemainingMillis("a@test.com")).isEqualTo(900_000L);
        clock.advance(Duration.ofMinutes(10));
        assertThat(store.lockRemainingMillis("a@test.com")).isEqualTo(300_000L);
        clock.advance(Duration.ofMinutes(5));
        assertThat(store.lockRemainingMillis("a@test.com")).isZero();
    }

    @Test
    void fourFailuresDoNotLock() {
        for (int i = 0; i < 4; i++) store.recordFailure("a@test.com");
        assertThat(store.lockRemainingMillis("a@test.com")).isZero();
        assertThat(store.failures("a@test.com")).isEqualTo(4);
    }

    @Test
    void memoryStaysBoundedOldEntriesAreEvicted() {
        for (int i = 0; i <= 10_000; i++) store.recordFailure("user" + i + "@test.com");   // 10,001 tracked
        for (int i = 0; i < 5; i++) store.recordFailure("locked@test.com");
        clock.advance(Duration.ofMinutes(16));                     // every window is over, no lock is active

        store.recordFailure("new@test.com");                       // over the limit -> stale entries go

        assertThat(store.tracked()).isEqualTo(1);
        assertThat(store.failures("new@test.com")).isEqualTo(1);
    }

    @Test
    void activeLocksSurviveTheEviction() {
        InMemoryLoginAttemptStore longLock =
                new InMemoryLoginAttemptStore(clock, 5, Duration.ofMinutes(15), Duration.ofMinutes(30));
        for (int i = 0; i <= 10_000; i++) longLock.recordFailure("user" + i + "@test.com");
        for (int i = 0; i < 5; i++) longLock.recordFailure("locked@test.com");   // locked for 30 minutes
        clock.advance(Duration.ofMinutes(16));                     // windows over, the lock is still active

        longLock.recordFailure("new@test.com");

        assertThat(longLock.lockRemainingMillis("locked@test.com")).isEqualTo(14 * 60_000L);
        assertThat(longLock.tracked()).isEqualTo(2);
    }

    @Test
    void unknownEmailIsNeitherLockedNorCounted() {
        assertThat(store.lockRemainingMillis("nobody@test.com")).isZero();
        assertThat(store.failures("nobody@test.com")).isZero();
    }

    @Test
    void exactlyTenThousandTrackedIsStillBelowTheCleanupLimit() {
        for (int i = 0; i < 10_000; i++) store.recordFailure("user" + i + "@test.com");
        clock.advance(Duration.ofMinutes(16));
        store.recordFailure("new@test.com");                       // 10,000 is not MORE than the limit
        assertThat(store.tracked()).isEqualTo(10_001);
    }
}
