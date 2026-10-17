package com.rbctcsworld.ecommerce.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * The hash chain that makes the audit trail tamper-evident.
 *
 * hash(row) = SHA-256 of the previous row's hash and every field of this row. Each part is written as
 * "length:value" (null as "-1:"), so no two different rows can produce the same text ("ab"+"c" vs "a"+"bc").
 * Changing a value, deleting a row or swapping two rows changes a hash that a later row depends on.
 */
public final class AuditChain {

    public static final String GENESIS = "0".repeat(64);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private AuditChain() {
    }

    public static String hash(String prevHash, long seq, LocalDateTime occurredAt, String actor, String entityType,
                              long entityId, String action, String field, String oldValue, String newValue) {
        StringBuilder s = new StringBuilder();
        for (String part : new String[]{prevHash, Long.toString(seq), occurredAt.format(TIME), actor, entityType,
                Long.toString(entityId), action, field, oldValue, newValue}) {
            s.append(part == null ? -1 : part.length()).append(':').append(part == null ? "" : part).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JVM", e);
        }
    }

    static String hashOf(AuditRecord r) {
        return hash(r.prevHash(), r.seq(), r.occurredAt(), r.actor(), r.entityType(), r.entityId(), r.action(),
                r.field(), r.oldValue(), r.newValue());
    }

    /** Result of checking the whole trail. brokenAtSeq = the first row that does not fit (null when valid). */
    public record Verification(boolean valid, int entries, Long brokenAtSeq, String reason) {
    }

    /**
     * Checks rows in seq order: numbering without gaps, each row linked to the one before, each hash correct,
     * and the last row is the one the head points to (otherwise the newest rows were removed).
     */
    public static Verification verify(List<AuditRecord> rows, long headSeq, String headHash) {
        Verification chain = verify(rows);
        if (!chain.valid()) return chain;
        String lastHash = rows.isEmpty() ? GENESIS : rows.get(rows.size() - 1).hash();
        if (rows.size() != headSeq || !lastHash.equals(headHash)) {
            return new Verification(false, rows.size(), (long) rows.size() + 1, "the newest rows are missing");
        }
        return chain;
    }

    /** Checks rows in seq order: numbering without gaps, each row linked to the one before, each hash correct. */
    public static Verification verify(List<AuditRecord> rows) {
        String expectedPrev = GENESIS;
        long expectedSeq = 1;
        for (AuditRecord r : rows) {
            if (r.seq() != expectedSeq) {
                return new Verification(false, rows.size(), r.seq(), "row " + expectedSeq + " is missing");
            }
            if (!r.prevHash().equals(expectedPrev)) {
                return new Verification(false, rows.size(), r.seq(), "not linked to the row before");
            }
            if (!hashOf(r).equals(r.hash())) {
                return new Verification(false, rows.size(), r.seq(), "content does not match its hash");
            }
            expectedPrev = r.hash();
            expectedSeq++;
        }
        return new Verification(true, rows.size(), null, null);
    }
}
