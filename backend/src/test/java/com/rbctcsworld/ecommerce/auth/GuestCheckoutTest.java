package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guest checkout accounts: REQ-AUTH-15. */
@ExtendWith(MockitoExtension.class)
class GuestCheckoutTest {

    @Mock UserRepository repo;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock LoginAttemptService attempts;
    @Mock RefreshTokenService refreshTokens;
    @InjectMocks AuthService service;

    @Test
    void aGuestGetsACustomerSessionWithAPasswordNobodyKnows() {
        when(repo.findByEmail("guest@test.com")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("hash-of-random");
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.generate("guest@test.com")).thenReturn("token");
        when(jwt.expiresInSeconds()).thenReturn(900L);
        when(refreshTokens.issue(any(AppUser.class))).thenReturn("refresh-1");

        AuthService.AuthResult r = service.guest("  Guest@Test.com ");

        assertThat(r.guest()).isTrue();
        assertThat(r.email()).isEqualTo("guest@test.com");
        assertThat(r.role()).isEqualTo(Role.CUSTOMER);
        assertThat(r.token()).isEqualTo("token");
        assertThat(r.refreshToken()).isEqualTo("refresh-1");
        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().isGuest()).isTrue();
        assertThat(saved.getValue().getPassword()).isEqualTo("hash-of-random");
        ArgumentCaptor<String> secret = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(secret.capture());
        assertThat(secret.getValue()).hasSizeGreaterThanOrEqualTo(43);       // 32 random bytes, Base64
    }

    @Test
    void twoGuestsNeverShareASecret() {
        when(repo.findByEmail(anyString())).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenAnswer(inv -> "h:" + inv.getArgument(0));
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        service.guest("a@test.com");
        service.guest("b@test.com");
        ArgumentCaptor<String> secrets = ArgumentCaptor.forClass(String.class);
        verify(encoder, times(2)).encode(secrets.capture());
        assertThat(secrets.getAllValues().get(0)).isNotEqualTo(secrets.getAllValues().get(1));
    }

    @Test
    void anEmailWithAnAccountCannotCheckOutAsAGuest() {
        when(repo.findByEmail("ada@test.com")).thenReturn(Optional.of(new AppUser("ada@test.com", "x")));
        assertThatThrownBy(() -> service.guest("ada@test.com"))
                .isInstanceOf(ConflictException.class).hasMessage(AuthService.HAS_ACCOUNT);
        verify(repo, never()).save(any());
    }

    @Test
    void aSecondGuestCheckoutWithTheSameEmailIsToldToSetAPassword() {
        when(repo.findByEmail("g@test.com")).thenReturn(Optional.of(AppUser.guest("g@test.com", "x")));
        assertThatThrownBy(() -> service.guest("G@test.com"))
                .isInstanceOf(ConflictException.class).hasMessage(AuthService.GUEST_AGAIN)
                .hasMessageContaining("Forgot password");
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void normalAccountsAreNotGuestsAndAClaimedGuestIsNoLongerOne() {
        AppUser normal = new AppUser("n@test.com", "x");
        assertThat(normal.isGuest()).isFalse();
        AppUser guest = AppUser.guest("g@test.com", "x");
        assertThat(guest.getRole()).isEqualTo(Role.CUSTOMER);
        guest.claim();
        assertThat(guest.isGuest()).isFalse();
    }

    @Test
    void aLoginOrRegisterResultSaysItIsNotAGuest() {
        when(repo.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(encoder.encode("Password1!")).thenReturn("hash");
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.register("new@test.com", "Password1!").guest()).isFalse();
    }
}
