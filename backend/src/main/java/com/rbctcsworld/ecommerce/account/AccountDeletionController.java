package com.rbctcsworld.ecommerce.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/account/delete {"password": "..."} (signed in): 204 deleted | 400 wrong password, guest or staff |
 * 409 an order or return still in progress. A POST rather than a DELETE with a body, which some proxies drop.
 */
@RestController
public class AccountDeletionController {

    record DeleteRequest(@NotBlank @Size(max = 200) String password) {
    }

    private final AccountDeletion deletion;

    public AccountDeletionController(AccountDeletion deletion) {
        this.deletion = deletion;
    }

    @PostMapping("/api/account/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @Valid @RequestBody DeleteRequest r) {
        deletion.delete(auth.getName(), r.password());
    }
}
