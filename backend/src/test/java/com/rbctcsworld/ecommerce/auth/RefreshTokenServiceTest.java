package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock RefreshTokenRepository repo;
    @Mock UserRepository users;
    private RefreshTokenService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repo, users, CLOCK, 14, 30);
        user = new AppUser("a@test.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    private RefreshToken stored(String raw, String family, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        RefreshToken t = new RefreshToken(7L, RefreshTokenService.hash(raw), family, NOW.minusDays(1), expiresAt);
        ReflectionTestUtils.setField(t, "id", 3L);
        ReflectionTestUtils.setField(t, "revokedAt", revokedAt);
        return t;
    }

    private RefreshToken saved() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void issueCreatesANewFamilyStoresOnlyTheHashAndCleansUpExpiredTokens() {
        String raw = service.issue(user);

        RefreshToken t = saved();
        assertThat(raw).matches("^[A-Za-z0-9_-]{43}$");                      // 256 bits, base64url
        assertThat(t.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(t.getUserId()).isEqualTo(7L);
        assertThat(t.getFamilyId()).hasSize(36);
        assertThat(t.getCreatedAt()).isEqualTo(NOW);
        assertThat(t.getExpiresAt()).isEqualTo(NOW.plusDays(14));
        assertThat(t.getRevokedAt()).isNull();
        verify(repo).deleteExpired(7L, NOW);
    }

    @Test
    void everyLoginGetsADifferentTokenAndFamily() {
        String first = service.issue(user);
        String second = service.issue(user);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rotateUsesTheTokenUpAndReturnsASuccessorInTheSameFamily() {
        when(repo.findByTokenHash(RefreshTokenService.hash("old"))).thenReturn(Optional.of(stored("old", "fam-1", NOW.plusDays(3), null)));
        when(repo.revokeIfActive(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.of(user));

        RefreshTokenService.Rotated r = service.rotate("old");

        assertThat(r.user()).isSameAs(user);
        assertThat(r.refreshToken()).isNotEqualTo("old");
        RefreshToken next = saved();
        assertThat(next.getFamilyId()).isEqualTo("fam-1");
        assertThat(next.getTokenHash()).isEqualTo(RefreshTokenService.hash(r.refreshToken()));
        assertThat(next.getExpiresAt()).isEqualTo(NOW.plusDays(14));
        verify(repo, never()).revokeFamily(anyString(), any());
    }

    @Test
    void reusedTokenRevokesTheWholeFamily() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("used", "fam-2", NOW.plusDays(3), NOW.minusMinutes(5))));

        assertThatThrownBy(() -> service.rotate("used")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo).revokeFamily("fam-2", NOW);
        verify(repo, never()).revokeIfActive(any(), any());
        verify(repo, never()).save(any());
    }

    // ---------- grace period: a lost response is not theft ----------

    @Test
    void lostResponseWithinTheGracePeriodGetsAnotherSuccessor() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("t", "fam-g", NOW.plusDays(3), NOW.minusSeconds(29))));
        when(repo.existsByFamilyIdAndRevokedAtAfter("fam-g", NOW.minusSeconds(29))).thenReturn(false);
        when(repo.existsByFamilyIdAndRevokedAtIsNull("fam-g")).thenReturn(true);
        when(users.findById(7L)).thenReturn(Optional.of(user));

        assertThat(service.rotate("t").user()).isSameAs(user);
        assertThat(saved().getFamilyId()).isEqualTo("fam-g");
        verify(repo, never()).revokeFamily(anyString(), any());
    }

    @Test
    void graceEndsAfterExactlyThirtySeconds() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("t", "fam-g", NOW.plusDays(3), NOW.minusSeconds(30))));

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo).revokeFamily("fam-g", NOW);
    }

    @Test
    void successorAlreadyUsedMeansTheftEvenWithinTheGracePeriod() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("t", "fam-g", NOW.plusDays(3), NOW.minusSeconds(5))));
        when(repo.existsByFamilyIdAndRevokedAtAfter("fam-g", NOW.minusSeconds(5))).thenReturn(true);

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo).revokeFamily("fam-g", NOW);
        verify(repo, never()).save(any());
    }

    @Test
    void loggedOutSessionStaysClosedWithinTheGracePeriod() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("t", "fam-g", NOW.plusDays(3), NOW.minusSeconds(5))));
        when(repo.existsByFamilyIdAndRevokedAtAfter("fam-g", NOW.minusSeconds(5))).thenReturn(false);
        when(repo.existsByFamilyIdAndRevokedAtIsNull("fam-g")).thenReturn(false);

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void twoParallelRefreshesBothSucceed() {
        // the other request spent the token a moment ago: this one reads the new state and gets its own successor
        when(repo.findByTokenHash(anyString())).thenReturn(
                Optional.of(stored("t", "fam-p", NOW.plusDays(3), null)),
                Optional.of(stored("t", "fam-p", NOW.plusDays(3), NOW)));
        when(repo.revokeIfActive(3L, NOW)).thenReturn(0);
        when(repo.existsByFamilyIdAndRevokedAtAfter("fam-p", NOW)).thenReturn(false);
        when(repo.existsByFamilyIdAndRevokedAtIsNull("fam-p")).thenReturn(true);
        when(users.findById(7L)).thenReturn(Optional.of(user));

        assertThat(service.rotate("t").user()).isSameAs(user);
        verify(repo, never()).revokeFamily(anyString(), any());
    }

    @Test
    void parallelRefreshAgainstALogoutFails() {
        when(repo.findByTokenHash(anyString())).thenReturn(
                Optional.of(stored("t", "fam-l", NOW.plusDays(3), null)),
                Optional.of(stored("t", "fam-l", NOW.plusDays(3), NOW)));
        when(repo.revokeIfActive(3L, NOW)).thenReturn(0);
        when(repo.existsByFamilyIdAndRevokedAtAfter("fam-l", NOW)).thenReturn(false);
        when(repo.existsByFamilyIdAndRevokedAtIsNull("fam-l")).thenReturn(false);

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void expiredTokenIsRejectedExactlyAtItsExpiryTime() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("t", "fam-4", NOW, null)));

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo, never()).revokeIfActive(any(), any());
        verify(repo, never()).revokeFamily(anyString(), any());
    }

    @Test
    void tokenOneSecondBeforeExpiryStillWorks() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("t", "fam-5", NOW.plusSeconds(1), null)));
        when(repo.revokeIfActive(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.of(user));

        assertThat(service.rotate("t").user()).isSameAs(user);
    }

    @Test
    void unknownTokenIsRejected() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rotate("nope")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankTokenIsRejectedWithoutADatabaseLookup(String raw) {
        assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void deletedUserCannotRefresh() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(stored("t", "fam-6", NOW.plusDays(1), null)));
        when(repo.revokeIfActive(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void logoutRevokesTheFamily() {
        when(repo.findByTokenHash(RefreshTokenService.hash("t"))).thenReturn(Optional.of(stored("t", "fam-7", NOW.plusDays(1), null)));
        service.revoke("t");
        verify(repo).revokeFamily("fam-7", NOW);
    }

    @Test
    void logoutWithAnUnknownTokenDoesNothing() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        service.revoke("unknown");
        verify(repo, never()).revokeFamily(anyString(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void logoutWithoutATokenIsANoOp(String raw) {
        service.revoke(raw);
        verifyNoInteractions(repo);
    }

    @Test
    void lifetimeIsFourteenDaysInSeconds() {
        assertThat(service.lifetimeSeconds()).isEqualTo(14 * 24 * 3600L);
    }
}
