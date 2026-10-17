package com.rbctcsworld.ecommerce.customer;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.RefreshTokenRepository;
import com.rbctcsworld.ecommerce.auth.Role;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerDetail;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerOrder;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerPage;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerSummary;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.LastAddress;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Customer accounts for the store admin: browse, search, look at one customer, disable / enable.
 * Store admins are not listed and cannot be disabled here. Passwords are never read or shown.
 */
@Service
@Transactional
public class CustomerService {

    public static final int MAX_SIZE = 100;
    /** Sort keys the list accepts (anything else: 400). Every order ends with the id, so pages are stable. */
    public static final Map<String, String> SORTS = Map.of(
            "newest", "u.created_at desc, u.id desc",
            "email", "u.email asc, u.id asc",
            "orders", "order_count desc, u.id asc",
            "spent", "spent desc, u.id asc",
            "lastOrder", "last_order_at desc nulls last, u.id asc");
    /** Statuses that count as money received (CANCELLED and RETURNED were refunded). */
    static final String PAID_STATUSES = "'PAID', 'SHIPPED', 'DELIVERED', 'RETURN_REQUESTED'";

    private static final String SELECT = """
            select u.id, u.email, u.active, u.created_at,
                   count(o.id) as order_count,
                   coalesce(sum(case when o.status in (%s) then o.total else 0 end), 0) as spent,
                   max(o.created_at) as last_order_at
            from users u left join orders o on o.user_id = u.id
            """.formatted(PAID_STATUSES);
    private static final String GROUP = " group by u.id, u.email, u.active, u.created_at";

    private static final RowMapper<CustomerSummary> SUMMARY = (rs, i) -> new CustomerSummary(rs.getLong("id"),
            rs.getString("email"), rs.getBoolean("active"), time(rs.getTimestamp("created_at")), rs.getLong("order_count"),
            rs.getBigDecimal("spent").setScale(2), time(rs.getTimestamp("last_order_at")));

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CustomerService(JdbcTemplate jdbc, UserRepository users, RefreshTokenRepository refreshTokens,
                           ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.clock = clock;
    }

    /**
     * q: part of the e-mail (case-insensitive); status: ACTIVE | DISABLED | null (all);
     * sort: newest (default) | email | orders | spent | lastOrder; page from 0; size 1..100 (default 20).
     */
    @Transactional(readOnly = true)
    public CustomerPage search(String q, String status, String sort, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int n = size == null ? 20 : size;
        if (p < 0) throw new BusinessRuleException("page must be 0 or more");
        if (n < 1 || n > MAX_SIZE) throw new BusinessRuleException("size must be between 1 and " + MAX_SIZE);
        String order = SORTS.get(sort == null || sort.isBlank() ? "newest" : sort.trim());
        if (order == null) throw new BusinessRuleException("sort must be one of " + new java.util.TreeSet<>(SORTS.keySet()));

        StringBuilder where = new StringBuilder(" where u.role = '" + Role.CUSTOMER + "'");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" and lower(u.email) like ? escape '\\'");
            args.add("%" + likeEscape(q.trim().toLowerCase(Locale.ROOT)) + "%");
        }
        if (status != null && !status.isBlank()) {
            switch (status.trim().toUpperCase(Locale.ROOT)) {
                case "ACTIVE" -> where.append(" and u.active = true");
                case "DISABLED" -> where.append(" and u.active = false");
                default -> throw new BusinessRuleException("status must be ACTIVE or DISABLED");
            }
        }
        Long total = jdbc.queryForObject("select count(*) from users u" + where, Long.class, args.toArray());
        List<CustomerSummary> items = jdbc.query(SELECT + where + GROUP + " order by " + order
                + " limit " + n + " offset " + ((long) p * n), SUMMARY, args.toArray());
        return new CustomerPage(items, total == null ? 0 : total, p, n);
    }

    /** The customer with their latest 50 orders and the last delivery address they used. 404 for admins too. */
    @Transactional(readOnly = true)
    public CustomerDetail detail(long id) {
        CustomerSummary summary = summary(id);
        List<CustomerOrder> orders = jdbc.query("""
                select id, order_number, status, total, created_at from orders
                where user_id = ? order by id desc limit 50""",
                (rs, i) -> new CustomerOrder(rs.getLong("id"), rs.getString("order_number"), rs.getString("status"),
                        rs.getBigDecimal("total"), time(rs.getTimestamp("created_at"))), id);
        List<LastAddress> address = jdbc.query("""
                select ship_name, ship_line1, ship_line2, ship_city, ship_state, ship_zip, ship_phone from orders
                where user_id = ? and ship_line1 is not null order by id desc limit 1""",
                (rs, i) -> new LastAddress(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)), id);
        return new CustomerDetail(summary, orders, address.isEmpty() ? null : address.get(0));
    }

    /**
     * Disables (active=false) or enables the account. Disabling also ends every session at once (refresh
     * tokens revoked; the access token is refused by JwtFilter on the next request). Setting the state it
     * already has changes nothing and records nothing.
     */
    public CustomerSummary setActive(long id, boolean active) {
        AppUser user = users.findById(id).filter(u -> Role.CUSTOMER.equals(u.getRole()))
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));
        if (user.isActive() != active) {
            user.setActive(active);
            if (!active) refreshTokens.revokeAllOf(id, LocalDateTime.now(clock));
            events.publishEvent(AuditEvent.of(List.of(new AuditChange(AuditChange.CUSTOMER, id,
                    active ? "CUSTOMER_ENABLED" : "CUSTOMER_DISABLED", "active",
                    Boolean.toString(!active), Boolean.toString(active)))));
        }
        users.flush();
        return summary(id);
    }

    private CustomerSummary summary(long id) {
        List<CustomerSummary> rows = jdbc.query(SELECT + " where u.id = ? and u.role = '" + Role.CUSTOMER + "'"
                + GROUP, SUMMARY, id);
        if (rows.isEmpty()) throw new NotFoundException("Customer not found: " + id);
        return rows.get(0);
    }

    /** % and _ are wildcards in LIKE: a search for "a_b" must find exactly "a_b". */
    static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static LocalDateTime time(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
