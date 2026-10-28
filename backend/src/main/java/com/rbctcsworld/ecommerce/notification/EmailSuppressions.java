package com.rbctcsworld.ecommerce.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
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

    static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
