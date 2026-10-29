package com.rbctcsworld.ecommerce.product;

import java.time.LocalDateTime;

/**
 * An uploaded product photo, already re-encoded by {@link PictureProcessor} (ADR-0017). Never changed, never deleted.
 * Stored through {@link ProductPictureStore} (plain JDBC: the bytes are only read when a picture is served).
 */
public record ProductPicture(String sha256, String contentType, int width, int height, int sizeBytes,
                             String uploadedBy, LocalDateTime uploadedAt) {

    /** Where the storefront loads a picture from; ProductRequest.imageUrl accepts exactly this form. */
    public static final String URL_PREFIX = "/api/products/pictures/";

    /** File name in the address: the SHA-256 of the bytes and the extension of their type. */
    public static final String FILE_NAME = "[0-9a-f]{64}\\.(jpg|png)";

    public String url() {
        return URL_PREFIX + sha256 + extension(contentType);
    }

    static String extension(String contentType) {
        return "image/png".equals(contentType) ? ".png" : ".jpg";
    }
}
