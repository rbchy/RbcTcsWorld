package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.notification.AccountEmailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder encoder;
    @Mock LoginAttemptService attempts;
    @Mock ApplicationEventPublisher events;
    private PasswordResetService service;
    private AppUser ada;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(users, tokens, refreshTokens, encoder, attempts, events, CLOCK,
                "https://shop.example.com/");
        ada = new AppUser("ada@test.com", "old-hash");
        ReflectionTestUtils.setField(ada, "id", 7L);
    }

    private AccountEmailEvent sentEmail() {
        ArgumentCaptor<AccountEmailEvent> e = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events).publishEvent(e.capture());
        return e.getValue();
    }

    private static PasswordResetToken token(LocalDateTime expiresAt, LocalDateTime usedAt) {
        PasswordResetToken t = new PasswordResetToken(7L, "hash", NOW.minusMinutes(5), expiresAt);
        ReflectionTestUtils.setField(t, "id", 3L);
        ReflectionTestUtils.setField(t, "usedAt", usedAt);
        return t;
    }

    // ---------------------------------------------------------------- request

    @Test
    void anUnknownEmailSilentlyGetsNothing() {
        when(users.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@test.com");

        verifyNoInteractions(tokens, events);
    }

    @Test
    void aKnownEmailGetsOneLinkThatOnlyItsHashIsStoredFor() {
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        when(tokens.countByUserIdAndCreatedAtAfter(7L, NOW.minusHours(1))).thenReturn(0L);

        service.requestReset("  ADA@Test.com ");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        InOrder order = inOrder(tokens);
        order.verify(tokens).useAllOf(7L, NOW);                     // older links stop working first
        order.verify(tokens).save(saved.capture());
        AccountEmailEvent mail = sentEmail();

        Matcher link = Pattern.compile("https://shop\\.example\\.com/#/reset-password\\?token=([A-Za-z0-9_-]{43})\\n")
                .matcher(mail.body());
        assertThat(link.find()).as("link in: " + mail.body()).isTrue();
        String raw = link.group(1);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(mail.recipient()).isEqualTo("ada@test.com");
        assertThat(mail.type()).isEqualTo(AccountEmailEvent.Type.PASSWORD_RESET);
        assertThat(mail.subject()).isEqualTo("Reset your RbcTcsWorld password");
        assertThat(mail.body()).contains("works once, for 30 minutes", "Did not ask for this?");
    }

    @Test
    void twoLinksGiveTwoDifferentTokens() {
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        service.requestReset("ada@test.com");
        service.requestReset("ada@test.com");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getTokenHash()).isNotEqualTo(saved.getAllValues().get(1).getTokenHash());
    }

    @Test
    void theThirdLinkInAnHourIsTheLast() {
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        when(tokens.countByUserIdAndCreatedAtAfter(7L, NOW.minusHours(1))).thenReturn(2L, 3L);

        service.requestReset("ada@test.com");     // 3rd link: sent
        verify(tokens).save(any());
        service.requestReset("ada@test.com");     // 4th: silently not sent

        verify(tokens).save(any());
        verify(events).publishEvent(any(AccountEmailEvent.class));
    }

    @Test
    void anEmptyEmailIsAnUnknownEmail() {
        when(users.findByEmail("")).thenReturn(Optional.empty());
        service.requestReset(null);
        verifyNoInteractions(tokens, events);
    }

    // ---------------------------------------------------------------- reset

    @Test
    void aValidLinkSetsTheNewPasswordAndEndsEverySession() {
        when(tokens.findByTokenHash(RefreshTokenService.hash("good"))).thenReturn(Optional.of(token(NOW.plusMinutes(1), null)));
        when(tokens.useIfUnused(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.of(ada));
        when(encoder.encode("NewPassword1!")).thenReturn("new-hash");

        service.resetPassword("good", "NewPassword1!");

        assertThat(ada.getPassword()).isEqualTo("new-hash");
        verify(users).save(ada);
        verify(tokens).useAllOf(7L, NOW);
        verify(refreshTokens).revokeAllOf(7L, NOW);
        verify(attempts).loginSucceeded("ada@test.com");
        AccountEmailEvent mail = sentEmail();
        assertThat(mail.type()).isEqualTo(AccountEmailEvent.Type.PASSWORD_CHANGED);
        assertThat(mail.recipient()).isEqualTo("ada@test.com");
        assertThat(mail.subject()).isEqualTo("Your RbcTcsWorld password was changed");
        assertThat(mail.body()).contains("every device was signed out", "Was this not you?");
    }

    @Test
    void aGuestWhoSetsAPasswordNowHasAFullAccount() {
        AppUser guest = AppUser.guest("guest@test.com", "random-hash");
        org.springframework.test.util.ReflectionTestUtils.setField(guest, "id", 7L);
        when(tokens.findByTokenHash(RefreshTokenService.hash("good"))).thenReturn(Optional.of(token(NOW.plusMinutes(1), null)));
        when(tokens.useIfUnused(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.of(guest));
        when(encoder.encode("NewPassword1!")).thenReturn("new-hash");

        service.resetPassword("good", "NewPassword1!");

        assertThat(guest.isGuest()).isFalse();
        assertThat(guest.getPassword()).isEqualTo("new-hash");
        verify(users).save(guest);
    }

    @Test
    void aLinkWorksUntilTheLastSecond() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.plusSeconds(1), null)));
        when(tokens.useIfUnused(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.of(ada));

        service.resetPassword("good", "NewPassword1!");

        verify(refreshTokens).revokeAllOf(7L, NOW);
    }

    private void assertRefused(String rawToken) {
        assertThatThrownBy(() -> service.resetPassword(rawToken, "NewPassword1!"))
                .isInstanceOf(BusinessRuleException.class).hasMessage(PasswordResetService.INVALID_LINK);
        verify(users, never()).save(any());
        verifyNoInteractions(refreshTokens, attempts, events, encoder);
    }

    @Test
    void anUnknownLinkIsRefused() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());
        assertRefused("made-up");
    }

    @Test
    void aMissingLinkIsRefused() {
        assertRefused(null);
        assertRefused("  ");
        verifyNoInteractions(tokens);
    }

    @Test
    void anExpiredLinkIsRefused() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token(NOW, null)));   // expired this very second
        assertRefused("old");
        verify(tokens, never()).useIfUnused(anyLong(), any());
    }

    @Test
    void aUsedLinkIsRefused() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.plusMinutes(10), NOW.minusMinutes(1))));
        assertRefused("used");
        verify(tokens, never()).useIfUnused(anyLong(), any());
    }

    @Test
    void aLinkUsedAtTheSameMomentByAnotherRequestIsRefused() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.plusMinutes(10), null)));
        when(tokens.useIfUnused(3L, NOW)).thenReturn(0);                // the other request won
        assertRefused("raced");
    }

    @Test
    void aLinkOfADeletedAccountIsRefused() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.plusMinutes(10), null)));
        when(tokens.useIfUnused(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.empty());
        assertRefused("orphan");
    }

    @Test
    void thePublicUrlWithoutATrailingSlashIsUsedAsItIs() {
        PasswordResetService plain = new PasswordResetService(users, tokens, refreshTokens, encoder, attempts, events,
                CLOCK, "http://localhost:5173");
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        plain.requestReset("ada@test.com");
        assertThat(sentEmail().body()).contains("http://localhost:5173/#/reset-password?token=");
    }
}
