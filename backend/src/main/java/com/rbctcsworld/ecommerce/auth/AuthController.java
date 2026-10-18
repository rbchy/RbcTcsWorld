package com.rbctcsworld.ecommerce.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/auth/register -> 201 {token,email,role,refreshToken,expiresIn} | 400 validation | 409 duplicate email
 * POST /api/auth/login    -> 200 {token,email,role,refreshToken,expiresIn} | 400 | 401 bad credentials | 429 locked
 * POST /api/auth/refresh  -> 200 {token,email,role,refreshToken,expiresIn} | 400 | 401 invalid/expired/reused
 * POST /api/auth/logout   -> 204 (always, also for an unknown token)
 * POST /api/auth/forgot-password {email}             -> 202 (always the same answer) | 400 validation
 * POST /api/auth/reset-password  {token, newPassword} -> 204 | 400 invalid/used/expired link or weak password
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    record AuthRequest(@Email @NotBlank String email, @NotBlank @Size(min = 8, max = 72) String password) {
    }

    record GuestRequest(@Email @NotBlank @Size(max = 255) String email) {
    }

    record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    record LogoutRequest(@Size(max = 200) String refreshToken) {
    }

    record ForgotPasswordRequest(@Email @NotBlank @Size(max = 255) String email) {
    }

    record ResetPasswordRequest(@NotBlank @Size(max = 200) String token,
                                @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    /** The answer to every forgot-password request, whether or not the e-mail has an account. */
    record Accepted(String message) {
        static final Accepted RESET_REQUESTED = new Accepted(
                "If an account exists for this e-mail, we have sent a link to reset the password.");
    }

    private final AuthService service;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService service, PasswordResetService passwordReset) {
        this.service = service;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthService.AuthResult register(@Valid @RequestBody AuthRequest r) {
        return service.register(r.email(), r.password());
    }

    /** Guest checkout: 201 {token, ..., guest: true} | 400 bad e-mail | 409 the e-mail already has an account. */
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    AuthService.AuthResult guest(@Valid @RequestBody GuestRequest r) {
        return service.guest(r.email());
    }

    @PostMapping("/login")
    AuthService.AuthResult login(@Valid @RequestBody AuthRequest r) {
        return service.login(r.email(), r.password());
    }

    @PostMapping("/refresh")
    AuthService.AuthResult refresh(@Valid @RequestBody RefreshRequest r) {
        return service.refresh(r.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody LogoutRequest r) {
        service.logout(r.refreshToken());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Accepted forgotPassword(@Valid @RequestBody ForgotPasswordRequest r) {
        passwordReset.requestReset(r.email());
        return Accepted.RESET_REQUESTED;
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest r) {
        passwordReset.resetPassword(r.token(), r.newPassword());
    }
}
