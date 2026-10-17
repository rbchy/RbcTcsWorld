package com.rbctcsworld.ecommerce.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String sku,
        @Size(max = 100) String category,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer stock,
        /**
         * Optional picture: the storefront's own image (/products/name.svg|png|jpg|webp) or an https:// link.
         * Anything else (javascript:, data:, http://, quotes or spaces) is refused with 400.
         */
        @Size(max = 500)
        @Pattern(regexp = "^(/products/[a-z0-9][a-z0-9-]*\\.(svg|png|jpg|webp)|https://[^\\s\"'<>]+)$",
                message = "must be /products/<name>.svg|png|jpg|webp or an https:// link")
        String imageUrl,
        /** Variant group shared by the options of one item: letters, digits and dashes, 2-40 (stored in upper case). */
        @Size(max = 40) @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z0-9][A-Za-z0-9-]{1,39}\\s*$",
                message = "must be 2-40 letters, digits or dashes") String variantGroup,
        /** S, M, XL, 10.5, 12 oz ... */
        @Size(max = 20) @Pattern(regexp = "^[\\p{L}\\p{N} ./-]*$", message = "may only contain letters, digits, spaces, . / and -")
        String size,
        /** Navy, Heather Grey ... */
        @Size(max = 30) @Pattern(regexp = "^[\\p{L} -]*$", message = "may only contain letters, spaces and -")
        String color) {

    /** Without variant (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock, String imageUrl) {
        this(name, sku, category, price, stock, imageUrl, null, null, null);
    }

    /** Without a picture (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock) {
        this(name, sku, category, price, stock, null);
    }
}
