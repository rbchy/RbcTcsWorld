package com.rbctcsworld.ecommerce.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Addresses that hard-bounced or complained: never e-mailed again (SES: SesEventController fills it). */
@Component
public class EmailSuppressions {

    public static final String BOUNCE = "BOUNCE";
    public static final String COMPLAINT = "COMPLAINT";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EmailSuppressions(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The reason (BOUNCE | COMPLAINT), or null when the address may be e-mailed. Case does not matter. */
    public String reason(String email) {
        return jdbc.queryForList("SELECT reason FROM email_suppressions WHERE email = ?", String.class, key(email))
                .stream().findFirst().orElse(null);
    }

    /** Idempotent: a repeated report keeps the first entry. Returns true when the address was added now. */
    public boolean suppress(String email, String reason, String detail) {
        if (reason(email) != null) {
            return false;
        }
        String d = detail == null ? null : detail.length() <= 300 ? detail : detail.substring(0, 297) + "...";
        jdbc.update("INSERT INTO email_suppressions (email, reason, detail, created_at) VALUES (?, ?, ?, ?)",
                key(email), reason, d, Timestamp.from(clock.instant()));
        return true;
    }

    /** One blocked address as the admin sees it. */
    public record Suppression(String email, String reason, String detail, LocalDateTime createdAt) {
    }

    /** Newest first, at most {@value #LIST_LIMIT}; {@code search} (optional) matches part of the address. */
    public List<Suppression> list(String search) {
        String like = search == null || search.isBlank() ? "%" : "%" + key(search).replace("%", "").replace("_", "") + "%";
        return jdbc.query("SELECT email, reason, detail, created_at FROM email_suppressions WHERE email LIKE ? "
                        + "ORDER BY created_at DESC, email LIMIT " + LIST_LIMIT,
                (rs, n) -> new Suppression(rs.getString("email"), rs.getString("reason"), rs.getString("detail"),
                        rs.getTimestamp("created_at").toLocalDateTime()), like);
    }

    public static final int LIST_LIMIT = 200;

    /**
     * Lets the address be e-mailed again (the customer fixed the mailbox, or a complaint was a mistake). Returns the
     * removed entry, or null when the address was not on the list. If it bounces again, SES puts it back.
     */
    public Suppression lift(String email) {
        if (email == null || email.isBlank()) return null;
        List<Suppression> found = jdbc.query("SELECT email, reason, detail, created_at FROM email_suppressions WHERE email = ?",
                (rs, n) -> new Suppression(rs.getString("email"), rs.getString("reason"), rs.getString("detail"),
                        rs.getTimestamp("created_at").toLocalDateTime()), key(email));
        if (found.isEmpty()) return null;
        jdbc.update("DELETE FROM email_suppressions WHERE email = ?", key(email));
        return found.get(0);
    }

    static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
