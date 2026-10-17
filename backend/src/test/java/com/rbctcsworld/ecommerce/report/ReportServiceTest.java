package com.rbctcsworld.ecommerce.report;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.report.ReportService.Options;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The reports against a small, known data set (H2, the columns the reports read). */
class ReportServiceTest {

    /** "today" = 2026-10-17 */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private ReportService service;

    @BeforeEach
    void setUp() {
        ds = new SingleConnectionDataSource("jdbc:h2:mem:rep-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table orders (id bigint primary key, status varchar(20), total_items int, "
                + "subtotal numeric(12,2), discount numeric(12,2), shipping_fee numeric(12,2), tax numeric(12,2), "
                + "total numeric(12,2), coupon_code varchar(40), created_at timestamp, paid_at timestamp)");
        jdbc.execute("create table order_items (id bigint primary key, order_id bigint, product_id bigint, "
                + "sku varchar(100), product_name varchar(255), quantity int, line_total numeric(12,2))");
        jdbc.execute("create table return_requests (id bigint primary key, reason varchar(20), status varchar(10), "
                + "refund_amount numeric(12,2), restocked boolean, created_at timestamp)");
        jdbc.execute("create table coupons (id bigint primary key, code varchar(40), type varchar(10), "
                + "discount_value numeric(12,2), active boolean)");
        jdbc.execute("create table products (id bigint primary key, sku varchar(100), name varchar(255), "
                + "category varchar(100), stock int, price numeric(12,2), active boolean)");
        jdbc.execute("create table warehouses (id bigint primary key, code varchar(10), city varchar(60), "
                + "state varchar(2), priority int, active boolean)");
        jdbc.execute("create table warehouse_stock (warehouse_id bigint, product_id bigint, quantity int)");
        service = new ReportService(jdbc, CLOCK);

        // id, status, items, subtotal, discount, shipping, tax, total, coupon, created, paid
        order(1, "DELIVERED", 2, "20.00", "0.00", "5.99", "1.20", "27.19", null, D1.atTime(9, 0), D1.atTime(9, 5));
        order(2, "PAID", 1, "60.00", "6.00", "0.00", "3.24", "57.24", "SAVE10", D1.atTime(23, 59), D1.plusDays(1).atTime(0, 1));
        order(3, "CANCELLED", 1, "10.00", "0.00", "5.99", "0.60", "16.59", "SAVE10", D1.plusDays(2).atTime(10, 0), D1.plusDays(2).atTime(10, 1));
        order(4, "RETURNED", 1, "15.00", "0.00", "5.99", "0.90", "21.89", null, D1.plusDays(2).atTime(11, 0), D1.plusDays(2).atTime(11, 1));
        order(5, "PLACED", 3, "30.00", "0.00", "5.99", "1.80", "37.79", null, D1.plusDays(3).atTime(8, 0), null);
        order(6, "SHIPPED", 1, "40.00", "0.00", "5.99", "2.40", "48.39", null, D1.minusDays(1).atTime(8, 0), D1.minusDays(1).atTime(8, 0));
        item(1, 1, 7, "LAMP", "Lamp", 2, "20.00");
        item(2, 2, 8, "DESK", "Desk", 1, "60.00");
        item(3, 3, 7, "LAMP", "Lamp", 1, "10.00");
        item(4, 4, 9, "MUG", "Mug", 1, "15.00");
        item(5, 6, 9, "MUG", "Mug", 3, "40.00");
        jdbc.update("insert into return_requests values (1, 'DAMAGED', 'APPROVED', 21.89, false, ?)", D1.plusDays(4).atTime(9, 0));
        jdbc.update("insert into return_requests values (2, 'DAMAGED', 'REQUESTED', null, false, ?)", D1.plusDays(5).atTime(9, 0));
        jdbc.update("insert into return_requests values (3, 'WRONG_ITEM', 'APPROVED', 10.00, true, ?)", D1.plusDays(5).atTime(9, 0));
        jdbc.update("insert into coupons values (1, 'SAVE10', 'PERCENT', 10, true), (2, 'OLD5', 'FIXED', 5, false)");
        jdbc.update("insert into products values (7, 'LAMP', 'Lamp', 'home', 3, 10.00, true), "
                + "(8, 'DESK', 'Desk', 'home', 0, 60.00, true), (9, 'MUG', 'Mug', null, 50, 5.50, true), "
                + "(10, 'GONE', 'Gone', 'home', 1, 99.00, false), (11, 'PEN', 'Pen', 'office', 5, 1.25, true)");
        jdbc.update("insert into warehouses values (1, 'PIT', 'Pittsburgh', 'PA', 10, true), "
                + "(2, 'NWK', 'Newark', 'NJ', 30, true), (3, 'OLD', 'Erie', 'PA', 5, false)");
        // LAMP 3 = 2 PIT + 1 NWK; DESK 0 in PIT; MUG 50 in NWK; PEN 5 in PIT; GONE (not on sale) 1 in PIT
        jdbc.update("insert into warehouse_stock values (1, 7, 2), (2, 7, 1), (1, 8, 0), (2, 9, 50), (1, 11, 5), (1, 10, 1)");
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    private void order(long id, String status, int items, String sub, String disc, String ship, String tax, String total,
                       String coupon, LocalDateTime created, LocalDateTime paid) {
        jdbc.update("insert into orders values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, status, items, new BigDecimal(sub),
                new BigDecimal(disc), new BigDecimal(ship), new BigDecimal(tax), new BigDecimal(total), coupon, created, paid);
    }

    private void item(long id, long order, long product, String sku, String name, int qty, String lineTotal) {
        jdbc.update("insert into order_items values (?, ?, ?, ?, ?, ?, ?)", id, order, product, sku, name, qty,
                new BigDecimal(lineTotal));
    }

    private static Options range(LocalDate from, LocalDate to) {
        return new Options(from, to, null, null, null);
    }

    private static List<Object> r(Object... v) {
        return Arrays.asList(v);
    }

    private static BigDecimal m(String v) {
        return new BigDecimal(v);
    }

    @Test
    void salesPerDayCountOnlyMoneyReceivedOnThePaymentDayAndListQuietDays() {
        ReportTable t = service.run("sales", range(D1, D1.plusDays(3)));

        assertThat(t.columns()).containsExactly("day", "orders", "items", "subtotal", "discount", "shipping", "tax", "total");
        assertThat(t.from()).isEqualTo(D1);
        assertThat(t.to()).isEqualTo(D1.plusDays(3));
        assertThat(t.rows()).containsExactly(
                r("2026-10-01", 1L, 2L, m("20.00"), m("0.00"), m("5.99"), m("1.20"), m("27.19")),
                r("2026-10-02", 1L, 1L, m("60.00"), m("6.00"), m("0.00"), m("3.24"), m("57.24")),
                r("2026-10-03", 0L, 0L, m("0.00"), m("0.00"), m("0.00"), m("0.00"), m("0.00")),   // cancelled + returned
                r("2026-10-04", 0L, 0L, m("0.00"), m("0.00"), m("0.00"), m("0.00"), m("0.00")));  // placed, not paid
        assertThat(t.totals()).isEqualTo(r("total", 2L, 3L, m("80.00"), m("6.00"), m("5.99"), m("4.44"), m("84.43")));
    }

    @Test
    void salesPerMonthAndTheDefaultRangeIsTheLast30Days() {
        ReportTable months = service.run("sales", new Options(LocalDate.of(2026, 9, 15), D1.plusDays(3), " MONTH ", null, null));
        assertThat(months.columns().get(0)).isEqualTo("month");
        assertThat(months.rows()).extracting(row -> row.get(0)).containsExactly("2026-09", "2026-10");
        assertThat(months.rows().get(0).get(7)).isEqualTo(m("48.39"));
        assertThat(months.rows().get(1).get(7)).isEqualTo(m("84.43"));

        ReportTable last30 = service.run(" Sales ", Options.none());
        assertThat(last30.from()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(last30.to()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(last30.rows()).hasSize(30);
        assertThat(service.run("sales", new Options(null, D1, null, null, null)).from()).isEqualTo(D1.minusDays(29));
        assertThatThrownBy(() -> service.run("sales", new Options(D1, D1, "week", null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("groupBy must be day or month");
    }

    @Test
    void theRangeIsCheckedAndOneDayIsAllowed() {
        assertThat(service.run("sales", range(D1, D1)).rows()).hasSize(1);
        assertThat(service.run("sales", range(D1, D1.plusDays(365))).rows()).hasSize(366);
        assertThatThrownBy(() -> service.run("sales", range(D1, D1.plusDays(366))))
                .isInstanceOf(BusinessRuleException.class).hasMessage("a report covers at most 366 days");
        assertThatThrownBy(() -> service.run("sales", range(D1.plusDays(1), D1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("from must be on or before to");
        assertThatThrownBy(() -> service.run("salary", Options.none())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.run(null, Options.none())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void topProductsByUnitsThenMoney() {
        ReportTable t = service.run("top-products", range(D1.minusDays(1), D1.plusDays(3)));
        assertThat(t.columns()).containsExactly("sku", "name", "units", "orders", "revenue");
        assertThat(t.rows()).containsExactly(r("MUG", "Mug", 3L, 1L, m("40.00")), r("LAMP", "Lamp", 2L, 1L, m("20.00")),
                r("DESK", "Desk", 1L, 1L, m("60.00")));
        assertThat(t.totals()).isEqualTo(r("total", null, 6L, null, m("120.00")));
        assertThat(service.run("top-products", new Options(D1.minusDays(1), D1.plusDays(3), null, 1, null)).rows()).hasSize(1);
        assertThat(service.run("top-products", new Options(D1.minusDays(1), D1.plusDays(3), null, 100, null)).rows()).hasSize(3);
        assertThatThrownBy(() -> service.run("top-products", new Options(null, null, null, 0, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> service.run("top-products", new Options(null, null, null, 101, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void ordersByStatusInLifecycleOrderWithEmptyStatusesListed() {
        ReportTable t = service.run("orders-by-status", range(D1, D1.plusDays(3)));
        assertThat(t.rows()).containsExactly(r("PLACED", 1L, m("37.79")), r("PAID", 1L, m("57.24")),
                r("SHIPPED", 0L, m("0.00")), r("DELIVERED", 1L, m("27.19")), r("RETURN_REQUESTED", 0L, m("0.00")),
                r("RETURNED", 1L, m("21.89")), r("CANCELLED", 1L, m("16.59")));
        assertThat(t.totals()).isEqualTo(r("total", 5L, m("160.70")));
    }

    @Test
    void returnsByReasonAndDecision() {
        ReportTable t = service.run("returns", range(D1, D1.plusDays(5)));
        assertThat(t.rows()).containsExactly(r("DAMAGED", "APPROVED", 1L, m("21.89"), 0L),
                r("DAMAGED", "REQUESTED", 1L, m("0.00"), 0L), r("WRONG_ITEM", "APPROVED", 1L, m("10.00"), 1L));
        assertThat(t.totals()).isEqualTo(r("total", null, 3L, m("31.89"), 1L));
        assertThat(service.run("returns", range(D1, D1.plusDays(4))).rows()).hasSize(1);
    }

    @Test
    void couponsWithTheirUseExcludingCancelledOrders() {
        ReportTable t = service.run("coupons", range(D1, D1.plusDays(3)));
        assertThat(t.rows()).containsExactly(r("SAVE10", "PERCENT", m("10.00"), "ACTIVE", 1L, m("6.00"), m("57.24")),
                r("OLD5", "FIXED", m("5.00"), "DISABLED", 0L, m("0.00"), m("0.00")));
        assertThat(t.totals()).isEqualTo(r("total", null, null, null, 1L, m("6.00"), m("57.24")));
    }

    @Test
    void lowStockIsASnapshotOfProductsOnSale() {
        ReportTable t = service.run("low-stock", Options.none());
        assertThat(t.from()).isNull();
        assertThat(t.totals()).isNull();
        assertThat(t.rows()).containsExactly(r("DESK", "Desk", "home", 0, m("60.00")), r("LAMP", "Lamp", "home", 3, m("10.00")),
                r("PEN", "Pen", "office", 5, m("1.25")));
        assertThat(service.run("low-stock", new Options(null, null, null, null, 0)).rows()).hasSize(1);
        assertThat(service.run("low-stock", new Options(null, null, null, null, 1000)).rows()).hasSize(4);
        assertThatThrownBy(() -> service.run("low-stock", new Options(null, null, null, null, -1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("threshold must be between 0 and 1000");
        assertThatThrownBy(() -> service.run("low-stock", new Options(null, null, null, null, 1001)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void inventoryValuePerCategory() {
        ReportTable t = service.run("inventory", Options.none());
        assertThat(t.rows()).containsExactly(r("(none)", 1L, 50L, m("275.00")), r("home", 2L, 3L, m("30.00")),
                r("office", 1L, 5L, m("6.25")));
        assertThat(t.totals()).isEqualTo(r("total", 4L, 58L, m("311.25")));
    }

    @Test
    void csvHasTheHeaderTheRowsAndTheTotals() {
        ReportTable t = service.run("inventory", Options.none());
        assertThat(service.csv(t)).isEqualTo("\"category\",\"products\",\"units\",\"value\"\r\n"
                + "\"(none)\",1,50,275.00\r\n\"home\",2,3,30.00\r\n\"office\",1,5,6.25\r\n\"total\",4,58,311.25\r\n");
        assertThat(service.csv(service.run("low-stock", new Options(null, null, null, null, 0))))
                .isEqualTo("\"sku\",\"name\",\"category\",\"stock\",\"price\"\r\n\"DESK\",\"Desk\",\"home\",0,60.00\r\n");
    }

    @Test
    void everyReportNameRuns() {
        for (String name : ReportService.NAMES) {
            assertThat(service.run(name, Options.none()).name()).isEqualTo(name);
        }
    }

    @Test
    void stockByWarehouseShowsWhereTheUnitsAndTheirValueAre() {
        ReportTable t = service.run("stock-by-warehouse", Options.none());
        assertThat(t.from()).isNull();
        assertThat(t.columns()).containsExactly("warehouse", "city", "usState", "state", "products", "units", "value");
        assertThat(t.rows()).containsExactly(
                r("OLD", "Erie", "PA", "DISABLED", 0L, 0L, m("0.00")),
                r("PIT", "Pittsburgh", "PA", "ACTIVE", 2L, 7L, m("26.25")),     // LAMP 2 x 10.00 + PEN 5 x 1.25; DESK 0 not counted
                r("NWK", "Newark", "NJ", "ACTIVE", 2L, 51L, m("285.00")));     // LAMP 1 x 10.00 + MUG 50 x 5.50
        assertThat(t.totals()).isEqualTo(r("total", null, null, null, null, 58L, m("311.25")));
    }
}
