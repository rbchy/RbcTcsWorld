package com.rbctcsworld.ecommerce.product;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** The product_pictures table (V27). Plain JDBC: metadata and bytes are read separately. */
@Repository
public class ProductPictureStore {

    private static final String COLUMNS = "sha256, content_type, width, height, size_bytes, uploaded_by, uploaded_at";

    private static final RowMapper<ProductPicture> PICTURE = (rs, n) -> new ProductPicture(rs.getString("sha256"),
            rs.getString("content_type"), rs.getInt("width"), rs.getInt("height"), rs.getInt("size_bytes"),
            rs.getString("uploaded_by"), rs.getTimestamp("uploaded_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public ProductPictureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductPicture> find(String sha256) {
        List<ProductPicture> found = jdbc.query("SELECT " + COLUMNS + " FROM product_pictures WHERE sha256 = ?", PICTURE, sha256);
        return found.stream().findFirst();
    }

    /** The picture's bytes, or empty when there is no picture of that name. */
    public Optional<byte[]> data(String sha256) {
        List<byte[]> found = jdbc.query("SELECT data FROM product_pictures WHERE sha256 = ?", (rs, n) -> rs.getBytes(1), sha256);
        return found.stream().findFirst();
    }

    /**
     * Saves the picture unless one with the same bytes is already there (then that one is kept, with its first
     * uploader). Returns the stored row and whether this call created it.
     */
    public Saved save(PictureProcessor.Picture p, String uploadedBy, LocalDateTime now) {
        Optional<ProductPicture> existing = find(p.sha256());
        if (existing.isPresent()) return new Saved(existing.get(), false);
        try {
            jdbc.update("INSERT INTO product_pictures (" + COLUMNS + ", data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    p.sha256(), p.contentType(), p.width(), p.height(), p.data().length, uploadedBy, Timestamp.valueOf(now), p.data());
            return new Saved(find(p.sha256()).orElseThrow(), true);
        } catch (DuplicateKeyException sameMomentSameBytes) {            // two uploads of one file at the same time
            return new Saved(find(p.sha256()).orElseThrow(), false);
        }
    }

    public record Saved(ProductPicture picture, boolean created) {
    }
}
