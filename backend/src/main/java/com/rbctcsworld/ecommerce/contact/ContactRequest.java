package com.rbctcsworld.ecommerce.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The contact form. {@code website} is a honeypot: a field people never see (hidden in the page), which bots fill in.
 * When the sender is logged in, {@code email} is ignored and the account's own address is used.
 */
public record ContactRequest(
        @NotBlank @Size(max = 100) String name,
        @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "ORDER|RETURN|PRODUCT|PAYMENT|OTHER", message = "must be ORDER, RETURN, PRODUCT, PAYMENT or OTHER")
        String topic,
        @Size(max = 40) @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z0-9-]{1,40}\\s*$", message = "may only contain letters, digits and -")
        String orderNumber,
        @NotBlank @Size(min = 10, max = 4000) String message,
        @Size(max = 200) String website) {
}
