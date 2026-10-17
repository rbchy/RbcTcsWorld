package com.rbctcsworld.ecommerce.report;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.report.PaymentLedgerService.Payment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentLedgerServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private PaymentLedgerService service;

    @BeforeEach
    void setUp() {
        ds = new SingleConnectionDataSource("jdbc:h2:mem:pay-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table orders (id bigint primary key, order_number varchar(30))");
        jdbc.execute("create table payment_transactions (id bigint primary key, order_id bigint, type varchar(10), "
                + "status varchar(10), amount numeric(12,2), card_last4 varchar(4), failure_reason varchar(100), "
                + "provider varchar(10), created_at timestamp)");
        jdbc.update("insert into orders values (1, 'ORD-2026-AAA'), (2, 'ORD-2026-BBB')");
        pay(1, 1, "CHARGE", "FAILED", "27.19", "card declined", D1.atTime(9, 0));
        pay(2, 1, "CHARGE", "SUCCEEDED", "27.19", null, D1.atTime(9, 1));
        pay(3, 2, "CHARGE", "SUCCEEDED", "50.00", null, D1.plusDays(1).atTime(10, 0));
        pay(4, 2, "REFUND", "SUCCEEDED", "50.00", null, D1.plusDays(2).atTime(10, 0));
        pay(5, 2, "CHARGE", "SUCCEEDED", "9.99", null, D1.minusDays(1).atTime(10, 0));
        service = new PaymentLedgerService(jdbc, CLOCK);
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    private void pay(long id, long order, String type, String status, String amount, String reason, java.time.LocalDateTime at) {
        jdbc.update("insert into payment_transactions values (?, ?, ?, ?, ?, '4242', ?, 'mock', ?)", id, order, type,
                status, new BigDecimal(amount), reason, at);
    }

    @Test
    void newestFirstWithTheSumsOfWhatReallyMoved() {
        var page = service.search(D1, D1.plusDays(2), null, null, null, null, null);
        assertThat(page.items()).extracting(Payment::id).containsExactly(4L, 3L, 2L, 1L);
        assertThat(page.total()).isEqualTo(4);
        assertThat(page.succeededCharges()).isEqualTo(new BigDecimal("77.19"));
        assertThat(page.succeededRefunds()).isEqualTo(new BigDecimal("50.00"));
        assertThat(page.items().get(3)).isEqualTo(new Payment(1, 1, "ORD-2026-AAA", "CHARGE", "FAILED",
                new BigDecimal("27.19"), "4242", "mock", "card declined", D1.atTime(9, 0)));
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void filtersCombine() {
        assertThat(service.search(D1, D1.plusDays(2), " refund ", null, null, null, null).items())
                .extracting(Payment::id).containsExactly(4L);
        assertThat(service.search(D1, D1.plusDays(2), "CHARGE", "failed", null, null, null).items())
                .extracting(Payment::id).containsExactly(1L);
        assertThat(service.search(D1, D1.plusDays(2), null, null, " bbb ", null, null).items())
                .extracting(Payment::id).containsExactly(4L, 3L);
        assertThat(service.search(D1, D1.plusDays(2), " ", " ", " ", null, null).total()).isEqualTo(4);
        assertThat(service.search(D1, D1, null, null, null, null, null).total()).isEqualTo(2);
        assertThat(service.search(D1, D1.plusDays(2), null, null, "%", null, null).total()).isEqualTo(4);
        var empty = service.search(D1, D1.plusDays(2), null, null, "ZZZ", null, null);
        assertThat(empty.total()).isZero();
        assertThat(empty.succeededCharges()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void theDefaultIsTheLast30DaysAndPagesAreStable() {
        assertThat(service.search(null, null, null, null, null, null, null).total()).isEqualTo(5);
        assertThat(service.search(null, D1, null, null, null, null, null).total()).isEqualTo(3);
        assertThat(service.search(D1, D1.plusDays(2), null, null, null, 1, 3).items()).extracting(Payment::id)
                .containsExactly(1L);
        assertThat(service.search(D1, D1.plusDays(2), null, null, null, 0, 1).items()).extracting(Payment::id)
                .containsExactly(4L);
    }

    @Test
    void wrongInputIsRefused() {
        assertThatThrownBy(() -> service.search(null, null, "GIFT", null, null, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("type must be CHARGE or REFUND");
        assertThatThrownBy(() -> service.search(null, null, null, "PENDING", null, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("status must be SUCCEEDED or FAILED");
        assertThatThrownBy(() -> service.search(D1, D1.minusDays(1), null, null, null, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("from must be on or before to");
        assertThatThrownBy(() -> service.search(D1, D1.plusDays(366), null, null, null, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("at most 366 days");
        assertThat(service.search(D1, D1.plusDays(365), null, null, null, null, null).total()).isEqualTo(4);
        assertThatThrownBy(() -> service.search(null, null, null, null, null, -1, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("page must be 0 or more");
        assertThatThrownBy(() -> service.search(null, null, null, null, null, 0, 0))
                .isInstanceOf(BusinessRuleException.class).hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> service.search(null, null, null, null, null, 0, 101))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(service.search(null, null, null, null, null, 0, 100).size()).isEqualTo(100);
    }
}
