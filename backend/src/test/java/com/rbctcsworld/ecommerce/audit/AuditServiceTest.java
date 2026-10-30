package com.rbctcsworld.ecommerce.audit;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * AuditService against a real in-memory database (the V14 migration on H2), without Spring:
 * fast enough for mutation testing, and the SQL is really run.
 */
class AuditServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T09:30:15.123456Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 9, 30, 15, 123_000_000);

    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private EntityManager em;
    private AuditService service;

    @BeforeEach
    void setUp() throws IOException {
        ds = new SingleConnectionDataSource("jdbc:h2:mem:audit-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
        jdbc = new JdbcTemplate(ds);
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V14__audit_log.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) jdbc.execute(statement);
            }
        }
        em = mock(EntityManager.class);
        service = new AuditService(jdbc, em, CLOCK);
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    private static AuditChange price(long productId, String from, String to) {
        return new AuditChange("PRODUCT", productId, "PRODUCT_UPDATED", "price", from, to);
    }

    @Test
    void eachChangeBecomesOneChainedRowAndTheHeadMoves() {
        service.on(new AuditEvent("admin@shop.test", List.of(price(5, "10.00", "12.50"),
                new AuditChange("PRODUCT", 5, "PRODUCT_UPDATED", "stock", "5", "9"))));

        List<AuditRecord> rows = service.find(null, null, null, null);
        assertThat(rows).hasSize(2);
        AuditRecord stock = rows.get(0);
        AuditRecord price = rows.get(1);
        assertThat(price).isEqualTo(new AuditRecord(1, NOW, "admin@shop.test", "PRODUCT", 5, "PRODUCT_UPDATED",
                "price", "10.00", "12.50", AuditChain.GENESIS, AuditChain.hash(AuditChain.GENESIS, 1, NOW,
                "admin@shop.test", "PRODUCT", 5, "PRODUCT_UPDATED", "price", "10.00", "12.50")));
        assertThat(stock.seq()).isEqualTo(2);
        assertThat(stock.prevHash()).isEqualTo(price.hash());
        assertThat(stock.hash()).isEqualTo(AuditChain.hashOf(stock));

        Map<String, Object> head = jdbc.queryForMap("select last_seq, last_hash from audit_head");
        assertThat(head).containsEntry("last_seq", 2L).containsEntry("last_hash", stock.hash());
        assertThat(service.verify()).isEqualTo(new AuditChain.Verification(true, 2, null, null));
    }

    @Test
    void pendingEntityChangesAreFlushedBeforeTheHeadIsLocked() {
        JdbcTemplate spyJdbc = org.mockito.Mockito.spy(jdbc);
        AuditService s = new AuditService(spyJdbc, em, CLOCK);
        s.on(new AuditEvent("a", List.of(price(1, "1", "2"))));
        var order = inOrder(em, spyJdbc);
        order.verify(em).flush();
        order.verify(spyJdbc).query(org.mockito.ArgumentMatchers.contains("for update"),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowCallbackHandler.class));
    }

    @Test
    void anEventWithoutChangesWritesNothing() {
        service.on(new AuditEvent("a", List.of()));
        verifyNoInteractions(em);
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select last_seq from audit_head", Long.class)).isZero();
    }

    @Test
    void laterEventsContinueTheSameChain() {
        service.on(new AuditEvent("a", List.of(price(1, "1", "2"))));
        service.on(new AuditEvent("b", List.of(price(1, "2", "3"))));
        List<AuditRecord> rows = service.find(null, null, null, null);
        assertThat(rows).extracting(AuditRecord::seq).containsExactly(2L, 1L);
        assertThat(rows.get(0).prevHash()).isEqualTo(rows.get(1).hash());
        assertThat(service.verify().valid()).isTrue();
    }

    @Test
    void aMissingHeadRowStopsTheWrite() {
        jdbc.update("delete from audit_head");
        assertThatThrownBy(() -> service.on(new AuditEvent("a", List.of(price(1, "1", "2")))))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit_head row is missing");
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isZero();
    }

    @Test
    void filtersAreCombinedAndForgiving() {
        service.on(new AuditEvent("Admin@Shop.test", List.of(price(1, "1", "2"), price(2, "1", "2"))));
        service.on(new AuditEvent("other@shop.test", List.of(price(1, "2", "3"),
                new AuditChange("ORDER", 1, "ORDER_STATUS", "status", "PLACED", "PAID"))));

        assertThat(service.find(" product ", null, null, null)).hasSize(3);
        assertThat(service.find("ORDER", null, null, null)).extracting(AuditRecord::seq).containsExactly(4L);
        assertThat(service.find("PRODUCT", 1L, null, null)).extracting(AuditRecord::seq).containsExactly(3L, 1L);
        assertThat(service.find(null, 1L, null, null)).hasSize(3);
        assertThat(service.find(null, null, " admin@shop.TEST ", null)).extracting(AuditRecord::seq)
                .containsExactly(2L, 1L);
        assertThat(service.find("PRODUCT", 1L, "other@shop.test", null)).extracting(AuditRecord::seq)
                .containsExactly(3L);
        assertThat(service.find(" ", null, " ", null)).hasSize(4);
        assertThat(service.find(null, null, null, 2)).extracting(AuditRecord::seq).containsExactly(4L, 3L);
    }

    @Test
    void theLimitMustBeBetweenOneAndOneThousand() {
        assertThat(service.find(null, null, null, 1)).isEmpty();
        assertThat(service.find(null, null, null, AuditService.MAX_LIMIT)).isEmpty();
        assertThatThrownBy(() -> service.find(null, null, null, 0)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("limit must be between 1 and 1000");
        assertThatThrownBy(() -> service.find(null, null, null, 1001)).isInstanceOf(BusinessRuleException.class);
        assertThat(AuditService.DEFAULT_LIMIT).isEqualTo(200);
    }

    @Test
    void theDefaultLimitIsTwoHundred() {
        List<AuditChange> many = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) many.add(price(i, "1", "2"));
        service.on(new AuditEvent("a", many));
        assertThat(service.find(null, null, null, null)).hasSize(200).first()
                .extracting(AuditRecord::seq).isEqualTo(201L);
    }

    @Test
    void verifyFindsAnEditedRowAndRemovedNewestRows() {
        service.on(new AuditEvent("a", List.of(price(1, "1", "2"), price(1, "2", "3"), price(1, "3", "4"))));
        jdbc.update("update audit_log set new_value = '0.01' where seq = 2");
        assertThat(service.verify()).isEqualTo(
                new AuditChain.Verification(false, 3, 2L, "content does not match its hash"));

        jdbc.update("update audit_log set new_value = '3' where seq = 2");
        jdbc.update("delete from audit_log where seq = 3");
        assertThat(service.verify()).isEqualTo(
                new AuditChain.Verification(false, 2, 3L, "the newest rows are missing"));
    }

    /**
     * DEF-030: an admin edit committed while the trail is being verified made an intact trail look tampered with
     * ("the newest rows are missing"), because the rows and the head were read in two statements. Here a second
     * writer commits exactly after the trail's rows are read; the verification must still see one moment.
     */
    @Test
    void aChangeCommittedWhileTheTrailIsVerifiedIsNotReportedAsTampering_DEF030() {
        service.on(new AuditEvent("a", List.of(price(1, "1", "2"), price(1, "2", "3"))));
        AuditService otherWriter = new AuditService(jdbc, em, CLOCK);
        boolean[] fired = {false};
        JdbcTemplate racing = new JdbcTemplate(ds) {
            private void afterTrailRead(String sql) {
                boolean readsTheTrailInOrder = sql.contains("audit_log") && sql.contains("order by") && !sql.contains("desc");
                if (readsTheTrailInOrder && !fired[0]) {
                    fired[0] = true;
                    otherWriter.append("other@shop.test", List.of(price(2, "5", "6")));    // commits in between
                }
            }

            @Override
            public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper) {
                List<T> result = super.query(sql, rowMapper);
                afterTrailRead(sql);
                return result;
            }

            @Override
            public void query(String sql, org.springframework.jdbc.core.RowCallbackHandler rch) {
                super.query(sql, rch);
                afterTrailRead(sql);
            }
        };

        AuditChain.Verification seen = new AuditService(racing, em, CLOCK).verify();

        assertThat(fired[0]).as("the other writer committed during the verification").isTrue();
        assertThat(seen).isEqualTo(new AuditChain.Verification(true, 2, null, null));     // the moment it read
        assertThat(service.verify()).isEqualTo(new AuditChain.Verification(true, 3, null, null));
    }

    @Test
    void aTrailWithoutItsHeadRowIsNotValid() {
        service.on(new AuditEvent("a", List.of(price(1, "1", "2"))));
        jdbc.update("delete from audit_head");
        assertThat(service.verify()).isEqualTo(new AuditChain.Verification(false, 1, 2L, "the newest rows are missing"));
    }

    @Test
    void anEmptyTrailVerifies() {
        assertThat(service.verify()).isEqualTo(new AuditChain.Verification(true, 0, null, null));
    }

    @Test
    void theCsvIsOldestFirstWithEveryColumn() {
        service.on(new AuditEvent("admin@shop.test", List.of(price(5, "10.00", "12.50"),
                new AuditChange("PRODUCT", 5, "PRODUCT_UPDATED", "name", "Say \"hi\"", "=HYPERLINK(\"x\")"))));
        List<AuditRecord> rows = service.find(null, null, null, null);

        String csv = service.csv(null, null, null);

        assertThat(csv.split("\r\n", -1)).containsExactly(
                "seq,occurred_at,actor,entity_type,entity_id,action,field,old_value,new_value,hash",
                "1,\"2026-10-17T09:30:15.123\",\"admin@shop.test\",\"PRODUCT\",5,\"PRODUCT_UPDATED\",\"price\","
                        + "\"10.00\",\"12.50\",\"" + rows.get(1).hash() + "\"",
                "2,\"2026-10-17T09:30:15.123\",\"admin@shop.test\",\"PRODUCT\",5,\"PRODUCT_UPDATED\",\"name\","
                        + "\"Say \"\"hi\"\"\",\"'=HYPERLINK(\"\"x\"\")\",\"" + rows.get(0).hash() + "\"",
                "");
        assertThat(service.csv("ORDER", null, null)).isEqualTo(
                "seq,occurred_at,actor,entity_type,entity_id,action,field,old_value,new_value,hash\r\n");
    }

    @Test
    void aCreatedRowHasAnEmptyOldValueCell() {
        service.on(new AuditEvent("a", List.of(new AuditChange("PRODUCT", 1, "PRODUCT_CREATED", "name", null, "L"))));
        assertThat(service.csv(null, null, null)).contains(",\"name\",,\"L\",");
    }

    @Test
    void cellsThatASpreadsheetWouldRunAsAFormulaAreDefused() {
        assertThat(AuditService.cell(null)).isEqualTo("");
        assertThat(AuditService.cell("")).isEqualTo("\"\"");
        assertThat(AuditService.cell("plain")).isEqualTo("\"plain\"");
        assertThat(AuditService.cell("a\"b")).isEqualTo("\"a\"\"b\"");
        for (String start : List.of("=", "+", "-", "@", "\t", "\r")) {
            assertThat(AuditService.cell(start + "1")).isEqualTo("\"'" + start + "1\"");
        }
        assertThat(AuditService.cell("1-2")).isEqualTo("\"1-2\"");
    }
}
