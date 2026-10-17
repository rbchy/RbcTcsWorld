package com.rbctcsworld.ecommerce.report;

import com.rbctcsworld.ecommerce.common.Csv;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Store reports for the admin. Dates are inclusive days (from 00:00 to the end of "to");
 * without dates a report covers the last 30 days up to today. At most 366 days per report.
 *
 * Money received = orders that were paid and not cancelled or returned (PAID, SHIPPED, DELIVERED,
 * RETURN_REQUESTED), counted on the day they were paid.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public static final List<String> NAMES = List.of("sales", "top-products", "orders-by-status", "returns",
            "coupons", "low-stock", "inventory");
    public static final int MAX_DAYS = 366;
    static final String PAID = "'PAID', 'SHIPPED', 'DELIVERED', 'RETURN_REQUESTED'";
    static final List<String> STATUS_ORDER = List.of("PLACED", "PAID", "SHIPPED", "DELIVERED", "RETURN_REQUESTED",
            "RETURNED", "CANCELLED");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Options a report may use; null = its default. */
    public record Options(LocalDate from, LocalDate to, String groupBy, Integer limit, Integer threshold) {
        public static Options none() {
            return new Options(null, null, null, null, null);
        }
    }

    public ReportTable run(String name, Options o) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "sales" -> sales(range(o), o.groupBy());
            case "top-products" -> topProducts(range(o), o.limit());
            case "orders-by-status" -> ordersByStatus(range(o));
            case "returns" -> returns(range(o));
            case "coupons" -> coupons(range(o));
            case "low-stock" -> lowStock(o.threshold());
            case "inventory" -> inventory();
            default -> throw new NotFoundException("No such report: " + name + " (reports: " + NAMES + ")");
        };
    }

    /** The report as CSV: header, rows, and a "total" line when it has one. */
    public String csv(ReportTable t) {
        StringBuilder s = new StringBuilder(Csv.line(t.columns()));
        t.rows().forEach(r -> s.append(Csv.line(r)));
        if (t.totals() != null) s.append(Csv.line(t.totals()));
        return s.toString();
    }

    // ------------------------------------------------------------------ the date range

    record Range(LocalDate from, LocalDate to) {
        LocalDateTime start() { return from.atStartOfDay(); }
        LocalDateTime end() { return to.plusDays(1).atStartOfDay(); }
    }

    Range range(Options o) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = o.to() == null ? today : o.to();
        LocalDate from = o.from() == null ? to.minusDays(29) : o.from();
        if (from.isAfter(to)) throw new BusinessRuleException("from must be on or before to");
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessRuleException("a report covers at most " + MAX_DAYS + " days");
        }
        return new Range(from, to);
    }

    // ------------------------------------------------------------------ the reports

    /** Money received per day or per month, every period listed (also the quiet ones). */
    ReportTable sales(Range r, String groupBy) {
        String g = groupBy == null || groupBy.isBlank() ? "day" : groupBy.trim().toLowerCase(Locale.ROOT);
        if (!g.equals("day") && !g.equals("month")) throw new BusinessRuleException("groupBy must be day or month");
        Map<String, BigDecimal[]> periods = new LinkedHashMap<>();       // orders, items, subtotal, discount, shipping, tax, total
        if (g.equals("day")) {
            for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) periods.put(d.toString(), zeros(7));
        } else {
            for (YearMonth m = YearMonth.from(r.from()); !m.isAfter(YearMonth.from(r.to())); m = m.plusMonths(1)) {
                periods.put(m.toString(), zeros(7));
            }
        }
        jdbc.query("select paid_at, total_items, subtotal, discount, shipping_fee, tax, total from orders "
                + "where paid_at >= ? and paid_at < ? and status in (" + PAID + ")", rs -> {
            LocalDate day = rs.getTimestamp("paid_at").toLocalDateTime().toLocalDate();
            BigDecimal[] p = periods.get(g.equals("day") ? day.toString() : YearMonth.from(day).toString());
            p[0] = p[0].add(BigDecimal.ONE);
            p[1] = p[1].add(BigDecimal.valueOf(rs.getInt("total_items")));
            p[2] = p[2].add(rs.getBigDecimal("subtotal"));
            p[3] = p[3].add(rs.getBigDecimal("discount"));
            p[4] = p[4].add(rs.getBigDecimal("shipping_fee"));
            p[5] = p[5].add(rs.getBigDecimal("tax"));
            p[6] = p[6].add(rs.getBigDecimal("total"));
        }, Timestamp.valueOf(r.start()), Timestamp.valueOf(r.end()));
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal[] sum = zeros(7);
        periods.forEach((period, p) -> {
            rows.add(row(period, p));
            for (int i = 0; i < 7; i++) sum[i] = sum[i].add(p[i]);
        });
        return new ReportTable("sales", r.from(), r.to(),
                List.of(g, "orders", "items", "subtotal", "discount", "shipping", "tax", "total"), rows, row("total", sum));
    }

    /** Best sellers by units, with the money they brought in (orders paid in the range). */
    ReportTable topProducts(Range r, Integer limit) {
        int n = limit == null ? 10 : limit;
        if (n < 1 || n > 100) throw new BusinessRuleException("limit must be between 1 and 100");
        List<List<Object>> rows = jdbc.query("""
                select oi.product_id, oi.sku, max(oi.product_name) as name, sum(oi.quantity) as units,
                       count(distinct oi.order_id) as orders, sum(oi.line_total) as revenue
                from order_items oi join orders o on o.id = oi.order_id
                where o.paid_at >= ? and o.paid_at < ? and o.status in (%s)
                group by oi.product_id, oi.sku
                order by units desc, revenue desc, oi.product_id
                limit %d""".formatted(PAID, n),
                (rs, i) -> List.<Object>of(rs.getString("sku"), rs.getString("name"), rs.getLong("units"),
                        rs.getLong("orders"), money(rs.getBigDecimal("revenue"))),
                Timestamp.valueOf(r.start()), Timestamp.valueOf(r.end()));
        return new ReportTable("top-products", r.from(), r.to(), List.of("sku", "name", "units", "orders", "revenue"),
                rows, totals(rows, 5, 2, 4));
    }

    /** Orders placed in the range, by where they are now (in lifecycle order). */
    ReportTable ordersByStatus(Range r) {
        Map<String, List<Object>> byStatus = new LinkedHashMap<>();
        STATUS_ORDER.forEach(s -> byStatus.put(s, null));
        jdbc.query("select status, count(*) as n, coalesce(sum(total), 0) as amount from orders "
                + "where created_at >= ? and created_at < ? group by status", rs -> {
            byStatus.put(rs.getString("status"), List.of(rs.getString("status"), rs.getLong("n"), money(rs.getBigDecimal("amount"))));
        }, Timestamp.valueOf(r.start()), Timestamp.valueOf(r.end()));
        List<List<Object>> rows = new ArrayList<>();
        byStatus.forEach((s, row) -> rows.add(row == null ? List.of(s, 0L, new BigDecimal("0.00")) : row));
        return new ReportTable("orders-by-status", r.from(), r.to(), List.of("status", "orders", "amount"), rows,
                totals(rows, 3, 1, 2));
    }

    /** Return requests made in the range: why, what was decided, how much was refunded, how many restocked. */
    ReportTable returns(Range r) {
        List<List<Object>> rows = jdbc.query("""
                select reason, status, count(*) as n, coalesce(sum(refund_amount), 0) as refunded,
                       sum(case when restocked then 1 else 0 end) as restocked
                from return_requests where created_at >= ? and created_at < ?
                group by reason, status order by reason, status""",
                (rs, i) -> List.<Object>of(rs.getString("reason"), rs.getString("status"), rs.getLong("n"),
                        money(rs.getBigDecimal("refunded")), rs.getLong("restocked")),
                Timestamp.valueOf(r.start()), Timestamp.valueOf(r.end()));
        return new ReportTable("returns", r.from(), r.to(), List.of("reason", "status", "returns", "refunded", "restocked"),
                rows, totals(rows, 5, 2, 3, 4));
    }

    /** Every coupon with what it did in the range (cancelled orders do not count). */
    ReportTable coupons(Range r) {
        List<List<Object>> rows = jdbc.query("""
                select c.code, c.type, c.discount_value, c.active, count(o.id) as uses,
                       coalesce(sum(o.discount), 0) as discount_given, coalesce(sum(o.total), 0) as order_total
                from coupons c left join orders o on o.coupon_code = c.code
                     and o.created_at >= ? and o.created_at < ? and o.status <> 'CANCELLED'
                group by c.code, c.type, c.discount_value, c.active
                order by uses desc, c.code""",
                (rs, i) -> List.<Object>of(rs.getString("code"), rs.getString("type"), money(rs.getBigDecimal("discount_value")),
                        rs.getBoolean("active") ? "ACTIVE" : "DISABLED", rs.getLong("uses"),
                        money(rs.getBigDecimal("discount_given")), money(rs.getBigDecimal("order_total"))),
                Timestamp.valueOf(r.start()), Timestamp.valueOf(r.end()));
        return new ReportTable("coupons", r.from(), r.to(),
                List.of("code", "type", "value", "state", "uses", "discount", "orderTotal"), rows, totals(rows, 7, 4, 5, 6));
    }

    /** Products on sale with this many or fewer in stock (default 5), emptiest first. A snapshot: no dates. */
    ReportTable lowStock(Integer threshold) {
        int t = threshold == null ? 5 : threshold;
        if (t < 0 || t > 1000) throw new BusinessRuleException("threshold must be between 0 and 1000");
        List<List<Object>> rows = jdbc.query("""
                select sku, name, category, stock, price from products
                where active = true and stock <= ? order by stock, id""",
                (rs, i) -> Arrays.<Object>asList(rs.getString("sku"), rs.getString("name"), rs.getString("category"),
                        rs.getInt("stock"), money(rs.getBigDecimal("price"))), t);
        return new ReportTable("low-stock", null, null, List.of("sku", "name", "category", "stock", "price"), rows, null);
    }

    /** What the stock on the shelves is worth, per category (products on sale). A snapshot: no dates. */
    ReportTable inventory() {
        List<List<Object>> rows = jdbc.query("""
                select coalesce(category, '(none)') as cat, count(*) as product_count, sum(stock) as unit_count,
                       sum(price * stock) as stock_value
                from products where active = true
                group by coalesce(category, '(none)') order by stock_value desc, cat""",
                (rs, i) -> List.<Object>of(rs.getString("cat"), rs.getLong("product_count"), rs.getLong("unit_count"),
                        money(rs.getBigDecimal("stock_value"))));
        return new ReportTable("inventory", null, null, List.of("category", "products", "units", "value"), rows,
                totals(rows, 4, 1, 2, 3));
    }

    // ------------------------------------------------------------------ helpers

    /** Money always with 2 decimals (databases differ in the scale of SUM and COALESCE). */
    static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    /** Label + the counts (orders, items) as whole numbers + the money columns with 2 decimals. */
    private static List<Object> row(String label, BigDecimal[] p) {
        List<Object> row = new ArrayList<>();
        row.add(label);
        row.add(p[0].longValue());
        row.add(p[1].longValue());
        for (int i = 2; i < p.length; i++) row.add(p[i].setScale(2, java.math.RoundingMode.HALF_UP));
        return row;
    }

    /**
     * The totals line: "total" in the first column, the sum of the given columns, null elsewhere.
     * width = number of columns; sums of whole numbers stay whole, money keeps 2 decimals.
     */
    static List<Object> totals(List<List<Object>> rows, int width, int... summed) {
        List<Object> t = new ArrayList<>(java.util.Collections.nCopies(width, null));
        t.set(0, "total");
        for (int c : summed) {
            boolean money = rows.stream().anyMatch(r -> r.get(c) instanceof BigDecimal);
            BigDecimal sum = BigDecimal.ZERO;
            for (List<Object> r : rows) sum = sum.add(new BigDecimal(r.get(c).toString()));
            t.set(c, money ? sum.setScale(2, java.math.RoundingMode.HALF_UP) : (Object) sum.longValue());
        }
        return t;
    }
}
