package com.rbctcsworld.ecommerce.audit;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChainTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 17, 9, 30, 15, 123_000_000);

    /** A correctly chained trail of n rows. */
    static List<AuditRecord> chain(int n) {
        List<AuditRecord> rows = new ArrayList<>();
        String prev = AuditChain.GENESIS;
        for (int i = 1; i <= n; i++) {
            String hash = AuditChain.hash(prev, i, T, "admin@shop.test", "PRODUCT", 5, "PRODUCT_UPDATED", "price",
                    "10.00", "1" + i + ".00");
            rows.add(new AuditRecord(i, T, "admin@shop.test", "PRODUCT", 5, "PRODUCT_UPDATED", "price", "10.00",
                    "1" + i + ".00", prev, hash));
            prev = hash;
        }
        return rows;
    }

    private static AuditRecord withNewValue(AuditRecord r, String newValue) {
        return new AuditRecord(r.seq(), r.occurredAt(), r.actor(), r.entityType(), r.entityId(), r.action(),
                r.field(), r.oldValue(), newValue, r.prevHash(), r.hash());
    }

    @Test
    void theGenesisIsSixtyFourZeros() {
        assertThat(AuditChain.GENESIS).hasSize(64).matches("0+");
    }

    @Test
    void theHashIsAKnownSha256OfLengthPrefixedFields() {
        // sha256("64:000...0|1:1|23:2026-10-17T09:30:15.123|1:a|7:PRODUCT|1:5|7:CREATED|4:name|-1:|4:Lamp|")
        String h = AuditChain.hash(AuditChain.GENESIS, 1, T, "a", "PRODUCT", 5, "CREATED", "name", null, "Lamp");
        assertThat(h).hasSize(64).matches("[0-9a-f]+");
        assertThat(h).isEqualTo(AuditChain.hash(AuditChain.GENESIS, 1, T, "a", "PRODUCT", 5, "CREATED", "name",
                null, "Lamp"));
        assertThat(h).isEqualTo(Sha.of("64:" + AuditChain.GENESIS + "|1:1|23:2026-10-17T09:30:15.123|1:a|7:PRODUCT|"
                + "1:5|7:CREATED|4:name|-1:|4:Lamp|"));
    }

    @Test
    void everyFieldIsPartOfTheHash() {
        String base = AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "f", "o", "n");
        assertThat(List.of(
                AuditChain.hash("q", 1, T, "a", "PRODUCT", 5, "X", "f", "o", "n"),
                AuditChain.hash("p", 2, T, "a", "PRODUCT", 5, "X", "f", "o", "n"),
                AuditChain.hash("p", 1, T.plusNanos(1_000_000), "a", "PRODUCT", 5, "X", "f", "o", "n"),
                AuditChain.hash("p", 1, T, "b", "PRODUCT", 5, "X", "f", "o", "n"),
                AuditChain.hash("p", 1, T, "a", "ORDER", 5, "X", "f", "o", "n"),
                AuditChain.hash("p", 1, T, "a", "PRODUCT", 6, "X", "f", "o", "n"),
                AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "Y", "f", "o", "n"),
                AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "g", "o", "n"),
                AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "f", "O", "n"),
                AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "f", "o", "N")))
                .doesNotContain(base).doesNotHaveDuplicates();
    }

    @Test
    void movingTextBetweenFieldsOrNullVersusEmptyGivesAnotherHash() {
        assertThat(AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "f", "ab", "c"))
                .isNotEqualTo(AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "f", "a", "bc"));
        assertThat(AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "f", null, "c"))
                .isNotEqualTo(AuditChain.hash("p", 1, T, "a", "PRODUCT", 5, "X", "f", "", "c"));
    }

    @Test
    void anUntouchedTrailIsValid() {
        List<AuditRecord> rows = chain(3);
        assertThat(AuditChain.verify(rows, 3, rows.get(2).hash()))
                .isEqualTo(new AuditChain.Verification(true, 3, null, null));
    }

    @Test
    void anEmptyTrailIsValidWhenTheHeadIsStillAtGenesis() {
        assertThat(AuditChain.verify(List.of(), 0, AuditChain.GENESIS))
                .isEqualTo(new AuditChain.Verification(true, 0, null, null));
    }

    @Test
    void anEditedValueIsFoundAtItsRow() {
        List<AuditRecord> rows = chain(3);
        rows.set(1, withNewValue(rows.get(1), "0.01"));
        assertThat(AuditChain.verify(rows, 3, rows.get(2).hash()))
                .isEqualTo(new AuditChain.Verification(false, 3, 2L, "content does not match its hash"));
    }

    @Test
    void aRemovedMiddleRowIsFound() {
        List<AuditRecord> rows = chain(3);
        rows.remove(1);
        assertThat(AuditChain.verify(rows, 3, rows.get(1).hash()))
                .isEqualTo(new AuditChain.Verification(false, 2, 3L, "row 2 is missing"));
    }

    @Test
    void aRowWithARecomputedHashStillBreaksTheLinkToTheNextRow() {
        List<AuditRecord> rows = chain(3);
        AuditRecord r = withNewValue(rows.get(1), "0.01");
        rows.set(1, new AuditRecord(r.seq(), r.occurredAt(), r.actor(), r.entityType(), r.entityId(), r.action(),
                r.field(), r.oldValue(), r.newValue(), r.prevHash(), AuditChain.hashOf(r)));
        assertThat(AuditChain.verify(rows, 3, rows.get(2).hash()))
                .isEqualTo(new AuditChain.Verification(false, 3, 3L, "not linked to the row before"));
    }

    @Test
    void theFirstRowMustStartFromGenesis() {
        List<AuditRecord> rows = chain(2);
        AuditRecord first = rows.get(0);
        String prev = "f".repeat(64);
        AuditRecord forged = new AuditRecord(1, T, first.actor(), first.entityType(), first.entityId(), first.action(),
                first.field(), first.oldValue(), first.newValue(), prev,
                AuditChain.hash(prev, 1, T, first.actor(), first.entityType(), first.entityId(), first.action(),
                        first.field(), first.oldValue(), first.newValue()));
        assertThat(AuditChain.verify(List.of(forged)))
                .isEqualTo(new AuditChain.Verification(false, 1, 1L, "not linked to the row before"));
    }

    @Test
    void removedNewestRowsAreFoundThroughTheHead() {
        List<AuditRecord> rows = chain(3);
        String headHash = rows.get(2).hash();
        List<AuditRecord> cut = rows.subList(0, 2);
        assertThat(AuditChain.verify(cut)).extracting(AuditChain.Verification::valid).isEqualTo(true);
        assertThat(AuditChain.verify(cut, 3, headHash))
                .isEqualTo(new AuditChain.Verification(false, 2, 3L, "the newest rows are missing"));
    }

    @Test
    void aHeadThatPointsElsewhereWithTheSameCountIsAlsoReported() {
        List<AuditRecord> rows = chain(2);
        assertThat(AuditChain.verify(rows, 2, "e".repeat(64)))
                .isEqualTo(new AuditChain.Verification(false, 2, 3L, "the newest rows are missing"));
        assertThat(AuditChain.verify(List.of(), 1, AuditChain.GENESIS))
                .isEqualTo(new AuditChain.Verification(false, 0, 1L, "the newest rows are missing"));
    }

    /** Independent SHA-256 for the known-answer test. */
    static final class Sha {
        static String of(String s) {
            try {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
