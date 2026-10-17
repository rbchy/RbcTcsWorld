package com.rbctcsworld.ecommerce.report;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every charge and refund (payment_transactions), newest first, for the store admin: the money trail
 * behind the orders. Only the last 4 card digits exist, so nothing more can be shown.
 */
@Service
@Transactional(readOnly = true)
public class PaymentLedgerService {

    public record Payment(long id, long orderId, String orderNumber, String type, String status, BigDecimal amount,
                          String cardLast4, String provider, String failureReason, LocalDateTime createdAt) {
    }

    public record PaymentPage(List<Payment> items, long total, BigDecimal succeededCharges,
                              BigDecimal succeededRefunds, int page, int size) {
    }

    private static final RowMapper<Payment> ROW = (rs, i) -> new Payment(rs.getLong("id"), rs.getLong("order_id"),
            rs.getString("order_number"), rs.getString("type"), rs.getString("status"), rs.getBigDecimal("amount"),
            rs.getString("card_last4"), rs.getString("provider"), rs.getString("failure_reason"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PaymentLedgerService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * from/to: inclusive days (default: the last 30 days, at most 366); type CHARGE | REFUND; status
     * SUCCEEDED | FAILED; q = part of the order number; page from 0, size 1..100 (default 20).
     */
    public PaymentPage search(LocalDate from, LocalDate to, String type, String status, String q,
                              Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int n = size == null ? 20 : size;
        if (p < 0) throw new BusinessRuleException("page must be 0 or more");
        if (n < 1 || n > 100) throw new BusinessRuleException("size must be between 1 and 100");
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new BusinessRuleException("from must be on or before to");
        if (ChronoUnit.DAYS.between(start, end) + 1 > ReportService.MAX_DAYS) {
            throw new BusinessRuleException("at most " + ReportService.MAX_DAYS + " days");
        }

        StringBuilder where = new StringBuilder(" where t.created_at >= ? and t.created_at < ?");
        List<Object> args = new ArrayList<>(List.of(Timestamp.valueOf(start.atStartOfDay()),
                Timestamp.valueOf(end.plusDays(1).atStartOfDay())));
        if (type != null && !type.isBlank()) {
            String t = type.trim().toUpperCase(Locale.ROOT);
            if (!t.equals("CHARGE") && !t.equals("REFUND")) throw new BusinessRuleException("type must be CHARGE or REFUND");
            where.append(" and t.type = ?");
            args.add(t);
        }
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase(Locale.ROOT);
            if (!s.equals("SUCCEEDED") && !s.equals("FAILED")) {
                throw new BusinessRuleException("status must be SUCCEEDED or FAILED");
            }
            where.append(" and t.status = ?");
            args.add(s);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and upper(o.order_number) like ?");
            args.add("%" + q.trim().toUpperCase(Locale.ROOT).replace("%", "").replace("_", "") + "%");
        }
        String from_ = " from payment_transactions t join orders o on o.id = t.order_id" + where;
        Long total = jdbc.queryForObject("select count(*)" + from_, Long.class, args.toArray());
        BigDecimal charges = jdbc.queryForObject("select coalesce(sum(t.amount), 0)" + from_
                + " and t.type = 'CHARGE' and t.status = 'SUCCEEDED'", BigDecimal.class, args.toArray());
        BigDecimal refunds = jdbc.queryForObject("select coalesce(sum(t.amount), 0)" + from_
                + " and t.type = 'REFUND' and t.status = 'SUCCEEDED'", BigDecimal.class, args.toArray());
        List<Payment> items = jdbc.query("select t.id, t.order_id, o.order_number, t.type, t.status, t.amount, "
                + "t.card_last4, t.provider, t.failure_reason, t.created_at" + from_
                + " order by t.created_at desc, t.id desc limit " + n + " offset " + ((long) p * n), ROW, args.toArray());
        return new PaymentPage(items, total == null ? 0 : total, money(charges), money(refunds), p, n);
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
