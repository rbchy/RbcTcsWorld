package com.rbctcsworld.ecommerce.qa.tests.db;

import com.rbctcsworld.ecommerce.qa.database.DatabaseUtils;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The audit trail in PostgreSQL: rows exist for an API change, and the database itself refuses UPDATE,
 * DELETE and TRUNCATE on audit_log (trigger) - so not even someone with SQL access can quietly edit history.
 */
@Tag("db")
@Tag("audit")
@Epic("Data integrity")
@Feature("Audit trail")
class AuditDatabaseTest {

    @BeforeAll
    static void databaseReachable() {
        assumeTrue(DatabaseUtils.isAvailable(), "PostgreSQL not reachable - DB validation skipped");
    }

    @Test
    @DisplayName("A new product writes chained audit rows and moves audit_head")
    void rowsAreChained() {
        long id = Fixtures.product("10.00", 2);
        List<Map<String, Object>> rows = DatabaseUtils.query(
                "select seq, prev_hash, hash from audit_log where entity_type = 'PRODUCT' and entity_id = ? order by seq", id);
        assertEquals(6, rows.size(), "name, sku, category, price, stock, active");
        for (int i = 1; i < rows.size(); i++) {
            assertEquals(rows.get(i - 1).get("hash"), rows.get(i).get("prev_hash"), "row " + i + " links to the one before");
        }
        long headSeq = ((Number) DatabaseUtils.scalar("select last_seq from audit_head where id = 1")).longValue();
        long maxSeq = ((Number) DatabaseUtils.scalar("select max(seq) from audit_log")).longValue();
        assertTrue(headSeq >= maxSeq, "the head is at (or after) the newest row");
    }

    @Test
    @Tag("security")
    @DisplayName("SECURITY: PostgreSQL refuses UPDATE, DELETE and TRUNCATE on audit_log")
    void appendOnly() {
        Fixtures.product("10.00", 1);
        assertAll(
                () -> assertTrue(DatabaseUtils.refusedWrite("update audit_log set new_value = 'x' where seq = (select max(seq) from audit_log)")
                        .contains("append-only"), "UPDATE"),
                () -> assertTrue(DatabaseUtils.refusedWrite("delete from audit_log where seq = (select max(seq) from audit_log)")
                        .contains("append-only"), "DELETE"),
                () -> assertTrue(DatabaseUtils.refusedWrite("truncate audit_log").contains("append-only"), "TRUNCATE"));
    }
}
