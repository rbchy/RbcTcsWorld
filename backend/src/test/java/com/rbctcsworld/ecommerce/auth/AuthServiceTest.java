package com.rbctcsworld.ecommerce.auth;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.InvalidCredentialsException;
import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository repo;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock LoginAttemptService attempts;
    @Mock RefreshTokenService refreshTokens;
    @InjectMocks AuthService service;

    @Test
    void registerNormalizesEmailAndCreatesCustomer() {
        when(repo.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(encoder.encode("Password1!")).thenReturn("hash");
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.generate("new@test.com")).thenReturn("token");
        when(jwt.expiresInSeconds()).thenReturn(900L);
        when(refreshTokens.issue(any(AppUser.class))).thenReturn("refresh-1");

        AuthService.AuthResult result = service.register("  New@Test.com ", "Password1!");

        assertThat(result.email()).isEqualTo("new@test.com");
        assertThat(result.role()).isEqualTo(Role.CUSTOMER);
        assertThat(result.token()).isEqualTo("token");
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    void registerDuplicateEmailIsConflict() {
        when(repo.findByEmail("dup@test.com")).thenReturn(Optional.of(new AppUser("dup@test.com", "x")));

        assertThatThrownBy(() -> service.register("dup@test.com", "Password1!"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginWithWrongPasswordIsInvalidCredentialsAndCountsAsFailure() {
        when(repo.findByEmail("a@test.com")).thenReturn(Optional.of(new AppUser("a@test.com", "hash")));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("a@test.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(attempts).loginFailed("a@test.com");
    }

    @Test
    void successfulLoginClearsFailures() {
        when(repo.findByEmail("a@test.com")).thenReturn(Optional.of(new AppUser("a@test.com", "hash")));
        when(encoder.matches("right", "hash")).thenReturn(true);
        when(jwt.generate("a@test.com")).thenReturn("token");
        when(refreshTokens.issue(any(AppUser.class))).thenReturn("refresh-2");

        AuthService.AuthResult result = service.login("A@test.com", "right");
        assertThat(result.token()).isEqualTo("token");
        assertThat(result.refreshToken()).isEqualTo("refresh-2");
        verify(attempts).loginSucceeded("a@test.com");
    }

    @Test
    void failedLoginIssuesNoRefreshToken() {
        when(repo.findByEmail("a@test.com")).thenReturn(Optional.of(new AppUser("a@test.com", "hash")));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("a@test.com", "wrong")).isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void refreshReturnsANewAccessTokenAndTheRotatedRefreshToken() {
        AppUser admin = new AppUser("boss@test.com", "hash", Role.ADMIN);
        when(refreshTokens.rotate("old")).thenReturn(new RefreshTokenService.Rotated(admin, "new"));
        when(jwt.generate("boss@test.com")).thenReturn("access");
        when(jwt.expiresInSeconds()).thenReturn(900L);

        AuthService.AuthResult r = service.refresh("old");

        assertThat(r.token()).isEqualTo("access");
        assertThat(r.refreshToken()).isEqualTo("new");
        assertThat(r.email()).isEqualTo("boss@test.com");
        assertThat(r.role()).isEqualTo(Role.ADMIN);                  // role comes from the database row
        assertThat(r.expiresIn()).isEqualTo(900L);
    }

    @Test
    void refreshWithAnInvalidTokenIsRejected() {
        when(refreshTokens.rotate("bad")).thenThrow(new com.rbctcsworld.ecommerce.common.exception.InvalidRefreshTokenException());
        assertThatThrownBy(() -> service.refresh("bad")).isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).generate(any());
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        service.logout("r");
        verify(refreshTokens).revoke("r");
    }

    @Test
    void unknownEmailStillRunsAPasswordCheckSoTimingRevealsNothing() {
        when(repo.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("dummy-hash");

        assertThatThrownBy(() -> service.login("ghost@test.com", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(encoder).matches("whatever", "dummy-hash");          // same bcrypt cost as a real account
        verify(attempts).loginFailed("ghost@test.com");             // unknown e-mails are counted too
    }

    @Test
    void lockedAccountIsRejectedBeforeThePasswordIsEvenChecked() {
        doThrow(new TooManyRequestsException("locked", 900)).when(attempts).checkAllowed("a@test.com");

        assertThatThrownBy(() -> service.login("a@test.com", "right"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(repo, never()).findByEmail(any());
        verify(encoder, never()).matches(any(), eq("hash"));
    }

    @Test
    void missingEmailIsAnInvalidLoginNotACrash() {
        when(repo.findByEmail(null)).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("dummy-hash");
        assertThatThrownBy(() -> service.login(null, "whatever")).isInstanceOf(InvalidCredentialsException.class);
    }
}
