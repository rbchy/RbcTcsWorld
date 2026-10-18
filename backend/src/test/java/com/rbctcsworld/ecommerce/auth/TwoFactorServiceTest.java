package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.InvalidCredentialsException;
import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import com.rbctcsworld.ecommerce.notification.AccountEmailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Two-factor sign-in: REQ-AUTH-16. */
@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock UserRepository users;
    @Mock LoginChallengeRepository challenges;
    @Mock PasswordEncoder encoder;
    @Mock ApplicationEventPublisher events;
    private TwoFactorService service;
    private AppUser ada;

    @BeforeEach
    void setUp() {
        service = new TwoFactorService(users, challenges, encoder, events, CLOCK);
        ada = new AppUser("ada@test.com", "hash");
        ReflectionTestUtils.setField(ada, "id", 7L);
    }

    /** Starts a sign-in and returns {challengeToken, code, stored challenge} like the browser and the inbox would see. */
    private Object[] begin() {
        TwoFactorService.Challenge c = service.begin(ada);
        ArgumentCaptor<AccountEmailEvent> mail = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events).publishEvent(mail.capture());
        Matcher m = Pattern.compile("code is (\\d{6})").matcher(mail.getValue().body());
        assertThat(m.find()).isTrue();
        ArgumentCaptor<LoginChallenge> saved = ArgumentCaptor.forClass(LoginChallenge.class);
        verify(challenges).save(saved.capture());
        ReflectionTestUtils.setField(saved.getValue(), "id", 3L);
        return new Object[] {c.challengeToken(), m.group(1), saved.getValue()};
    }

    @Test
    void beginEmailsASixDigitCodeAndStoresOnlyHashes() {
        Object[] b = begin();
        LoginChallenge stored = (LoginChallenge) b[2];
        assertThat((String) b[1]).matches("\\d{6}");
        assertThat(stored.getCodeHash()).hasSize(64).doesNotContain((String) b[1]);
        assertThat(stored.getChallengeHash()).hasSize(64).isNotEqualTo(b[0]);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        verify(challenges).useAllOf(7L, NOW);                       // older open codes die
    }

    @Test
    void theEmailTypeIsLoginCode() {
        service.begin(ada);
        ArgumentCaptor<AccountEmailEvent> mail = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events).publishEvent(mail.capture());
        assertThat(mail.getValue().type()).isEqualTo(AccountEmailEvent.Type.LOGIN_CODE);
        assertThat(mail.getValue().recipient()).isEqualTo("ada@test.com");
    }

    @Test
    void tooManyCodesInAnHourIs429() {
        when(challenges.countByUserIdAndCreatedAtAfter(7L, NOW.minusHours(1))).thenReturn(5L);
        assertThatThrownBy(() -> service.begin(ada)).isInstanceOf(TooManyRequestsException.class);
        verify(challenges, never()).save(any());
    }

    @Test
    void theRightCodeSignsTheUserIn() {
        Object[] b = begin();
        LoginChallenge stored = (LoginChallenge) b[2];
        when(challenges.findByChallengeHash(stored.getChallengeHash())).thenReturn(Optional.of(stored));
        when(challenges.useIfUnused(3L, NOW)).thenReturn(1);
        when(users.findById(7L)).thenReturn(Optional.of(ada));
        assertThat(service.verify((String) b[0], " " + b[1] + " ")).isSameAs(ada);
    }

    @Test
    void aWrongCodeIsCountedAndRefused() {
        Object[] b = begin();
        LoginChallenge stored = (LoginChallenge) b[2];
        when(challenges.findByChallengeHash(stored.getChallengeHash())).thenReturn(Optional.of(stored));
        String wrong = b[1].equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> service.verify((String) b[0], wrong)).isInstanceOf(BusinessRuleException.class)
                .hasMessage(TwoFactorService.INVALID);
        verify(challenges).countAttempt(3L);
        verify(challenges, never()).useIfUnused(anyLong(), any());
    }

    @Test
    void afterFiveWrongGuessesEvenTheRightCodeIsRefused() {
        Object[] b = begin();
        LoginChallenge stored = (LoginChallenge) b[2];
        ReflectionTestUtils.setField(stored, "attempts", 5);
        when(challenges.findByChallengeHash(stored.getChallengeHash())).thenReturn(Optional.of(stored));
        assertThatThrownBy(() -> service.verify((String) b[0], (String) b[1])).isInstanceOf(BusinessRuleException.class);
        verify(challenges, never()).useIfUnused(anyLong(), any());
    }

    @Test
    void usedExpiredAndUnknownChallengesAllGetTheSameAnswer() {
        Object[] b = begin();
        LoginChallenge stored = (LoginChallenge) b[2];
        when(challenges.findByChallengeHash(stored.getChallengeHash())).thenReturn(Optional.of(stored));
        ReflectionTestUtils.setField(stored, "usedAt", NOW.minusMinutes(1));
        assertThatThrownBy(() -> service.verify((String) b[0], (String) b[1])).hasMessage(TwoFactorService.INVALID);
        ReflectionTestUtils.setField(stored, "usedAt", null);
        ReflectionTestUtils.setField(stored, "expiresAt", NOW);       // end is exclusive
        assertThatThrownBy(() -> service.verify((String) b[0], (String) b[1])).hasMessage(TwoFactorService.INVALID);
        assertThatThrownBy(() -> service.verify("nope", "123456")).hasMessage(TwoFactorService.INVALID);
        assertThatThrownBy(() -> service.verify(null, "123456")).hasMessage(TwoFactorService.INVALID);
        assertThatThrownBy(() -> service.verify("", "123456")).hasMessage(TwoFactorService.INVALID);
    }

    @Test
    void twoParallelRequestsCannotBothUseTheCode() {
        Object[] b = begin();
        LoginChallenge stored = (LoginChallenge) b[2];
        when(challenges.findByChallengeHash(stored.getChallengeHash())).thenReturn(Optional.of(stored));
        when(challenges.useIfUnused(3L, NOW)).thenReturn(0);          // the other request won
        assertThatThrownBy(() -> service.verify((String) b[0], (String) b[1])).isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- turning it on and off

    @Test
    void turnOnNeedsThePasswordAndSendsAnEmail() {
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        when(encoder.matches("secret-123", "hash")).thenReturn(true);
        assertThat(service.setEnabled(" ADA@test.com ", true, "secret-123")).isTrue();
        assertThat(ada.isTwoFactorEnabled()).isTrue();
        ArgumentCaptor<AccountEmailEvent> mail = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events).publishEvent(mail.capture());
        assertThat(mail.getValue().type()).isEqualTo(AccountEmailEvent.Type.TWO_FACTOR_CHANGED);
    }

    @Test
    void aWrongPasswordChangesNothing() {
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        when(encoder.matches("bad", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.setEnabled("ada@test.com", true, "bad")).isInstanceOf(InvalidCredentialsException.class);
        assertThat(ada.isTwoFactorEnabled()).isFalse();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void aGuestCannotTurnItOn() {
        AppUser guest = AppUser.guest("g@test.com", "random");
        when(users.findByEmail("g@test.com")).thenReturn(Optional.of(guest));
        assertThatThrownBy(() -> service.setEnabled("g@test.com", true, "x")).hasMessage(TwoFactorService.GUEST);
    }

    @Test
    void settingTheSameValueAgainSendsNoEmail() {
        ada.setTwoFactorEnabled(true);
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        when(encoder.matches("secret-123", "hash")).thenReturn(true);
        service.setEnabled("ada@test.com", true, "secret-123");
        verify(events, never()).publishEvent(any(Object.class));
    }
}
