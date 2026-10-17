package com.rbctcsworld.ecommerce.customer;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.RefreshTokenRepository;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerSummary;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.LastAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CustomerService with its SQL really run (H2, the columns it reads) and the repositories mocked. */
class CustomerServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private UserRepository users;
    private RefreshTokenRepository refreshTokens;
    private ApplicationEventPublisher events;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        ds = new SingleConnectionDataSource("jdbc:h2:mem:cust-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH", "sa", "", true);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table users (id bigint primary key, email varchar(255), role varchar(50), "
                + "active boolean not null default true, created_at timestamp not null)");
        jdbc.execute("create table orders (id bigint primary key, order_number varchar(30), user_id bigint, "
                + "status varchar(20), total numeric(12,2), created_at timestamp, ship_name varchar(80), "
                + "ship_line1 varchar(100), ship_line2 varchar(100), ship_city varchar(60), ship_state varchar(2), "
                + "ship_zip varchar(10), ship_phone varchar(20))");
        users = mock(UserRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new CustomerService(jdbc, users, refreshTokens, events, CLOCK);

        user(1, "ada@test.com", true, 1);
        user(2, "bob_x@test.com", true, 2);
        user(3, "cy%z@test.com", false, 3);
        user(9, "admin@shop.test", true, 0);   // a store admin: never listed
        order(10, 1, "PAID", "30.00", 1);
        order(11, 1, "CANCELLED", "99.00", 2);
        order(12, 1, "DELIVERED", "20.50", 3);
        order(13, 2, "RETURNED", "50.00", 4);
        order(14, 2, "SHIPPED", "5.00", 5);
        order(15, 2, "PLACED", "7.00", 6);
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    private void user(long id, String email, boolean active, int day) {
        jdbc.update("insert into users values (?, ?, ?, ?, ?)", id, email, id == 9 ? "ADMIN" : "CUSTOMER", active,
                T0.plusDays(day));
    }

    private void order(long id, long userId, String status, String total, int day) {
        jdbc.update("insert into orders (id, order_number, user_id, status, total, created_at) values (?, ?, ?, ?, ?, ?)",
                id, "ORD-" + id, userId, status, new BigDecimal(total), T0.plusDays(10 + day));
    }

    private static List<String> emails(List<CustomerSummary> rows) {
        return rows.stream().map(CustomerSummary::email).toList();
    }

    @Test
    void theListShowsCustomersOnlyNewestFirstWithOrdersAndMoneyReceived() {
        var page = service.search(null, null, null, null, null);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(emails(page.items())).containsExactly("cy%z@test.com", "bob_x@test.com", "ada@test.com");
        CustomerSummary ada = page.items().get(2);
        assertThat(ada).isEqualTo(new CustomerSummary(1, "ada@test.com", true, T0.plusDays(1), 3,
                new BigDecimal("50.50"), T0.plusDays(13)));
        CustomerSummary bob = page.items().get(1);
        assertThat(bob.spent()).isEqualByComparingTo("5.00");        // RETURNED refunded, PLACED not paid yet
        CustomerSummary cy = page.items().get(0);
        assertThat(cy.orders()).isZero();
        assertThat(cy.spent()).isEqualByComparingTo("0.00");
        assertThat(cy.lastOrderAt()).isNull();
        assertThat(cy.active()).isFalse();
    }

    @Test
    void searchMatchesPartOfTheEmailLiterallyAndFiltersByStatus() {
        assertThat(emails(service.search(" ADA ", null, null, null, null).items())).containsExactly("ada@test.com");
        assertThat(emails(service.search("_", null, null, null, null).items())).containsExactly("bob_x@test.com");
        assertThat(emails(service.search("%", null, null, null, null).items())).containsExactly("cy%z@test.com");
        assertThat(emails(service.search(null, "disabled", null, null, null).items())).containsExactly("cy%z@test.com");
        assertThat(service.search("test", " ACTIVE ", null, null, null).total()).isEqualTo(2);
        assertThat(service.search("nobody", null, null, null, null).total()).isZero();
        assertThat(service.search(" ", " ", " ", null, null).total()).isEqualTo(3);
        assertThatThrownBy(() -> service.search(null, "GONE", null, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("status must be ACTIVE or DISABLED");
    }

    @Test
    void everySortOrderAndStablePages() {
        assertThat(emails(service.search(null, null, "email", null, null).items()))
                .containsExactly("ada@test.com", "bob_x@test.com", "cy%z@test.com");
        assertThat(emails(service.search(null, null, "orders", null, null).items()))
                .containsExactly("ada@test.com", "bob_x@test.com", "cy%z@test.com");
        assertThat(emails(service.search(null, null, "spent", null, null).items()))
                .containsExactly("ada@test.com", "bob_x@test.com", "cy%z@test.com");
        assertThat(emails(service.search(null, null, "lastOrder", null, null).items()))
                .containsExactly("bob_x@test.com", "ada@test.com", "cy%z@test.com");
        assertThat(emails(service.search(null, null, "email", 1, 2).items())).containsExactly("cy%z@test.com");
        assertThat(service.search(null, null, "email", 1, 2).total()).isEqualTo(3);
        assertThat(emails(service.search(null, null, "email", 0, 1).items())).containsExactly("ada@test.com");
        assertThatThrownBy(() -> service.search(null, null, "salary", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("sort must be one of [email, lastOrder, newest, orders, spent]");
    }

    @Test
    void pageAndSizeAreChecked() {
        assertThat(service.search(null, null, null, 0, 1).items()).hasSize(1);
        assertThat(service.search(null, null, null, 0, 100).items()).hasSize(3);
        assertThatThrownBy(() -> service.search(null, null, null, -1, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("page must be 0 or more");
        assertThatThrownBy(() -> service.search(null, null, null, 0, 0))
                .isInstanceOf(BusinessRuleException.class).hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> service.search(null, null, null, 0, 101))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theDetailHasTheOrdersNewestFirstAndTheLastAddress() {
        jdbc.update("update orders set ship_name = 'Ada L', ship_line1 = '1 Main St', ship_city = 'Pittsburgh', "
                + "ship_state = 'PA', ship_zip = '15213', ship_phone = '412-555-0100' where id = 10");
        jdbc.update("update orders set ship_name = 'Ada Lovelace', ship_line1 = '2 Oak Ave', ship_line2 = 'Apt 3', "
                + "ship_city = 'Erie', ship_state = 'PA', ship_zip = '16501', ship_phone = '814-555-0100' where id = 11");

        var d = service.detail(1);

        assertThat(d.customer().email()).isEqualTo("ada@test.com");
        assertThat(d.orders()).extracting(CustomerDtos.CustomerOrder::orderNumber)
                .containsExactly("ORD-12", "ORD-11", "ORD-10");
        assertThat(d.orders().get(0)).isEqualTo(new CustomerDtos.CustomerOrder(12, "ORD-12", "DELIVERED",
                new BigDecimal("20.50"), T0.plusDays(13)));
        assertThat(d.lastAddress()).isEqualTo(new LastAddress("Ada Lovelace", "2 Oak Ave", "Apt 3", "Erie", "PA",
                "16501", "814-555-0100"));
        assertThat(service.detail(3).lastAddress()).isNull();
        assertThat(service.detail(3).orders()).isEmpty();
    }

    @Test
    void anAdminOrAnUnknownIdIsNotACustomer() {
        assertThatThrownBy(() -> service.detail(9)).isInstanceOf(NotFoundException.class)
                .hasMessage("Customer not found: 9");
        assertThatThrownBy(() -> service.detail(404)).isInstanceOf(NotFoundException.class);
        AppUser admin = new AppUser("admin@shop.test", "x", "ADMIN");
        when(users.findById(9L)).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> service.setActive(9, false)).isInstanceOf(NotFoundException.class);
        assertThat(admin.isActive()).isTrue();
        when(users.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setActive(404, false)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void disablingEndsEverySessionAndIsAudited() {
        AppUser ada = new AppUser("ada@test.com", "x");
        ReflectionTestUtils.setField(ada, "id", 1L);
        when(users.findById(1L)).thenReturn(Optional.of(ada));

        service.setActive(1, false);

        assertThat(ada.isActive()).isFalse();
        verify(refreshTokens).revokeAllOf(1L, LocalDateTime.now(CLOCK));
        verify(events).publishEvent(AuditEvent.of(List.of(
                new AuditChange("CUSTOMER", 1, "CUSTOMER_DISABLED", "active", "true", "false"))));
        verify(users).flush();
    }

    @Test
    void enablingIsAuditedAndKeepsSessionsAsTheyAre() {
        AppUser cy = new AppUser("cy%z@test.com", "x");
        cy.setActive(false);
        when(users.findById(3L)).thenReturn(Optional.of(cy));

        CustomerSummary result = service.setActive(3, true);

        assertThat(cy.isActive()).isTrue();
        assertThat(result.email()).isEqualTo("cy%z@test.com");
        verify(refreshTokens, never()).revokeAllOf(anyLong(), any());
        verify(events).publishEvent(AuditEvent.of(List.of(
                new AuditChange("CUSTOMER", 3, "CUSTOMER_ENABLED", "active", "false", "true"))));
    }

    @Test
    void settingTheSameStateAgainChangesAndRecordsNothing() {
        AppUser ada = new AppUser("ada@test.com", "x");
        when(users.findById(1L)).thenReturn(Optional.of(ada));

        service.setActive(1, true);

        verify(refreshTokens, never()).revokeAllOf(anyLong(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(CustomerService.likeEscape("a%b_c\\d")).isEqualTo("a\\%b\\_c\\\\d");
    }
}
