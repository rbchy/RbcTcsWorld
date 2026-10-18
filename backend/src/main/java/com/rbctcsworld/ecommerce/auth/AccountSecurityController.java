package com.rbctcsworld.ecommerce.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/account/two-factor                    -> 200 {enabled}                      (signed in)
 * PUT /api/account/two-factor {enabled, password} -> 200 {enabled} | 400 guest | 401 wrong password
 */
@RestController
@RequestMapping("/api/account/two-factor")
public class AccountSecurityController {

    record State(boolean enabled) {
    }

    record Change(@NotNull Boolean enabled, @NotBlank @Size(max = 72) String password) {
    }

    private final TwoFactorService twoFactor;

    public AccountSecurityController(TwoFactorService twoFactor) {
        this.twoFactor = twoFactor;
    }

    @GetMapping
    State get(Authentication auth) {
        return new State(twoFactor.isEnabled(auth.getName()));
    }

    @PutMapping
    State put(Authentication auth, @Valid @RequestBody Change c) {
        return new State(twoFactor.setEnabled(auth.getName(), c.enabled(), c.password()));
    }
}
