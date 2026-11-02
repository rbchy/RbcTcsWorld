package com.rbctcsworld.ecommerce.account;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.RefreshTokenRepository;
import com.rbctcsworld.ecommerce.auth.Role;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.notification.AccountEmailEvent;
import com.rbctcsworld.ecommerce.order.OrderStatus;
import com.rbctcsworld.ecommerce.returns.ReturnRequest;
import com.rbctcsworld.ecommerce.review.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * A customer deletes their own account (ADR-0022, REQ-AUTH-17), in one transaction:
 *  - refused while anything is still open: an order not yet paid, paid or on its way, or a return being decided (409),
 *    so no parcel or refund is left without an address to tell;
 *  - refused for the wrong password (400), for a guest (no password) and for staff accounts;
 *  - removed: cart, wishlist, address book, saved cards, reviews (product ratings recalculated), sessions, reset links,
 *    sign-in codes, contact messages, e-mails still waiting in the outbox;
 *  - kept, as the privacy policy says: orders, payments, refunds and returns (tax and accounting law), coupon use, and
 *    the suppression list; the old address is replaced in the e-mails already sent;
 *  - the user row stays for those orders, with a placeholder e-mail, an unusable password and deleted_at;
 *  - one last e-mail to the old address confirms it; the audit trail records the deletion without the address.
 */
@Component
@Transactional
public class AccountDeletion {

    static final String WRONG_PASSWORD = "That is not your current password";
    static final String GUEST = "A guest checkout has no account to delete";
    static final String STAFF = "Staff accounts are removed by the store owner, not here";
    static final String OPEN = "You still have an order or a return in progress. Cancel it, or wait until it is "
            + "delivered or decided, then delete your account.";
    private static final Logger log = LoggerFactory.getLogger(AccountDeletion.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Statuses that still need the customer: unpaid, paid, on the way, return asked for. */
    static final List<String> OPEN_ORDERS = List.of(OrderStatus.PLACED, OrderStatus.PAID, OrderStatus.SHIPPED,
            OrderStatus.RETURN_REQUESTED);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ReviewService reviews;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountDeletion(UserRepository users, RefreshTokenRepository refreshTokens, ReviewService reviews,
                           JdbcTemplate jdbc, PasswordEncoder encoder, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.reviews = reviews;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.events = events;
        this.clock = clock;
    }

    public void delete(String email, String password) {
        AppUser user = users.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (user.isGuest()) throw new BusinessRuleException(GUEST);
        if (!Role.CUSTOMER.equals(user.getRole())) throw new BusinessRuleException(STAFF);
        if (password == null || !encoder.matches(password, user.getPassword())) {
            throw new BusinessRuleException(WRONG_PASSWORD);
        }
        long id = user.getId();
        if (hasOpenBusiness(id)) throw new ConflictException(OPEN);

        String oldEmail = user.getEmail();
        LocalDateTime now = LocalDateTime.now(clock);
        int removedReviews = reviews.removeAllBy(id);
        for (String table : List.of("cart_items", "wishlist_items", "customer_addresses", "payment_methods",
                "password_reset_tokens", "login_challenges")) {
            jdbc.update("delete from " + table + " where user_id = ?", id);
        }
        jdbc.update("delete from contact_messages where user_id = ? or email = ?", id, oldEmail);
        jdbc.update("delete from notifications where recipient = ? and status in ('PENDING', 'SENDING')", oldEmail);
        refreshTokens.revokeAllOf(id, now);

        user.erase(encoder.encode(token()), now);   // a random password nobody knows
        users.saveAndFlush(user);
        jdbc.update("update notifications set recipient = ? where recipient = ?", user.getEmail(), oldEmail);

        events.publishEvent(AuditEvent.of(List.of(new AuditChange(AuditChange.CUSTOMER, id, "CUSTOMER_DELETED_ACCOUNT",
                "deleted", "false", "true"))));
        events.publishEvent(new AccountEmailEvent(oldEmail, AccountEmailEvent.Type.ACCOUNT_DELETED,
                "Your RbcTcsWorld account was deleted",
                "Your RbcTcsWorld account was deleted, as you asked. Your address book, saved cards, wishlist, reviews "
                        + "and messages are gone, and we will not e-mail this address again.\n\n"
                        + "We keep your past orders, payments and refunds for as long as tax and accounting law "
                        + "requires; they are no longer linked to this e-mail address.\n\n"
                        + "Was this not you? Reply to this e-mail right away."));
        log.info("Customer {} deleted the account ({} reviews removed)", id, removedReviews);
    }

    /** An order still moving, or a return not yet decided. */
    boolean hasOpenBusiness(long userId) {
        String in = String.join(",", OPEN_ORDERS.stream().map(s -> "'" + s + "'").toList());
        Integer orders = jdbc.queryForObject("select count(*) from orders where user_id = ? and status in (" + in + ")",
                Integer.class, userId);
        Integer returns = jdbc.queryForObject("select count(*) from return_requests r join orders o on o.id = r.order_id "
                + "where o.user_id = ? and r.status = '" + ReturnRequest.REQUESTED + "'", Integer.class, userId);
        return (orders == null ? 0 : orders) + (returns == null ? 0 : returns) > 0;
    }

    private static String token() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
