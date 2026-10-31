package com.rbctcsworld.ecommerce.product;

import jakarta.validation.constraints.DecimalMax;
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
         * Optional picture: the storefront's own image (/products/name.svg|png|jpg|webp), an uploaded photo
         * (/api/products/pictures/<sha256>.jpg|png, ADR-0017) or an https:// link.
         * Anything else (javascript:, data:, http://, quotes or spaces) is refused with 400.
         */
        @Size(max = 500)
        @Pattern(regexp = "^(/products/[a-z0-9][a-z0-9-]*\\.(svg|png|jpg|webp)|/api/products/pictures/[0-9a-f]{64}\\.(jpg|png)|https://[^\\s\"'<>]+)$",
                message = "must be /products/<name>.svg|png|jpg|webp, an uploaded picture or an https:// link")
        String imageUrl,
        /** Variant group shared by the options of one item: letters, digits and dashes, 2-40 (stored in upper case). */
        @Size(max = 40) @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z0-9][A-Za-z0-9-]{1,39}\\s*$",
                message = "must be 2-40 letters, digits or dashes") String variantGroup,
        /** S, M, XL, 10.5, 12 oz ... */
        @Size(max = 20) @Pattern(regexp = "^[\\p{L}\\p{N} ./-]*$", message = "may only contain letters, digits, spaces, . / and -")
        String size,
        /** Navy, Heather Grey ... */
        @Size(max = 30) @Pattern(regexp = "^[\\p{L} -]*$", message = "may only contain letters, spaces and -")
        String color,
        /** Optional flash deal: a price below the normal price between two UTC times (all three, or none). */
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal dealPrice,
        java.time.LocalDateTime dealStartsAt,
        java.time.LocalDateTime dealEndsAt,
        /** Affiliate product (ADR-0020): the partner shop's https link (e.g. an Amazon link with your tag); empty = sold here. */
        @Size(max = 500) @Pattern(regexp = "^\\s*$|^\\s*https://[^\\s\"'<>]+\\s*$", message = "must be an https:// link")
        String affiliateUrl,
        /** The owner's own words about the product (ADR-0021): plain text, up to 4000 characters, no HTML. */
        @Size(max = 4000) @Pattern(regexp = "^[^<>]*$", message = "may not contain < or >") String description,
        /** Shipping weight in ounces, packed (ADR-0013): up to 150 lb, one decimal. Empty = 16 oz per item. */
        @Positive @DecimalMax("2400") @Digits(integer = 4, fraction = 1) BigDecimal weightOz,
        /** Size of the shipping box in inches, one decimal, at most 108 (the carriers' longest side); all three or none. */
        @Positive @DecimalMax("108") @Digits(integer = 3, fraction = 1) BigDecimal lengthIn,
        @Positive @DecimalMax("108") @Digits(integer = 3, fraction = 1) BigDecimal widthIn,
        @Positive @DecimalMax("108") @Digits(integer = 3, fraction = 1) BigDecimal heightIn,
        /** Stripe Tax product tax code (ADR-0012), e.g. txcd_30011000 for clothing; empty = the account's default. */
        @Pattern(regexp = "^\\s*$|^\\s*[tT][xX][cC][dD]_\\d{8}\\s*$", message = "must be a Stripe tax code such as txcd_99999999")
        String taxCode) {

    /** Without a tax code (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock, String imageUrl,
                          String variantGroup, String size, String color, BigDecimal dealPrice,
                          java.time.LocalDateTime dealStartsAt, java.time.LocalDateTime dealEndsAt, String affiliateUrl,
                          String description, BigDecimal weightOz, BigDecimal lengthIn, BigDecimal widthIn,
                          BigDecimal heightIn) {
        this(name, sku, category, price, stock, imageUrl, variantGroup, size, color, dealPrice, dealStartsAt, dealEndsAt,
                affiliateUrl, description, weightOz, lengthIn, widthIn, heightIn, null);
    }

    /** Without weight and size (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock, String imageUrl,
                          String variantGroup, String size, String color, BigDecimal dealPrice,
                          java.time.LocalDateTime dealStartsAt, java.time.LocalDateTime dealEndsAt, String affiliateUrl,
                          String description) {
        this(name, sku, category, price, stock, imageUrl, variantGroup, size, color, dealPrice, dealStartsAt, dealEndsAt,
                affiliateUrl, description, null, null, null, null, null);
    }

    /** Without a description (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock, String imageUrl,
                          String variantGroup, String size, String color, BigDecimal dealPrice,
                          java.time.LocalDateTime dealStartsAt, java.time.LocalDateTime dealEndsAt, String affiliateUrl) {
        this(name, sku, category, price, stock, imageUrl, variantGroup, size, color, dealPrice, dealStartsAt, dealEndsAt,
                affiliateUrl, null);
    }

    /** Without an affiliate link (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock, String imageUrl,
                          String variantGroup, String size, String color, BigDecimal dealPrice,
                          java.time.LocalDateTime dealStartsAt, java.time.LocalDateTime dealEndsAt) {
        this(name, sku, category, price, stock, imageUrl, variantGroup, size, color, dealPrice, dealStartsAt, dealEndsAt, null);
    }

    /** Without a deal (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock, String imageUrl,
                          String variantGroup, String size, String color) {
        this(name, sku, category, price, stock, imageUrl, variantGroup, size, color, null, null, null);
    }

    /** Without variant (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock, String imageUrl) {
        this(name, sku, category, price, stock, imageUrl, null, null, null, null, null, null);
    }

    /** Without a picture (older clients, tests). */
    public ProductRequest(String name, String sku, String category, BigDecimal price, Integer stock) {
        this(name, sku, category, price, stock, null);
    }
}
