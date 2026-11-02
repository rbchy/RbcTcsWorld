package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.order.CustomerOrder;
import com.rbctcsworld.ecommerce.order.OrderItem;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes customer e-mails into the outbox (table notifications) and lets admins see and re-queue them.
 *
 * The listener runs synchronously INSIDE the transaction that published the event (MANDATORY): the e-mail
 * row is committed together with the order change, or not at all. Sending happens later, outside that
 * transaction (NotificationDeliveryService), so a slow or broken mail server never blocks a checkout.
 */
@Service
public class NotificationService {

    record Email(String subject, String body) {
    }

    private final NotificationRepository notifications;
    private final OrderRepository orders;
    private final UserRepository users;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, OrderRepository orders, UserRepository users,
                               Clock clock) {
        this.notifications = notifications;
        this.orders = orders;
        this.users = users;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(OrderNotificationEvent event) {
        CustomerOrder order = orders.findById(event.orderId()).orElseThrow();
        String recipient = users.findById(order.getUserId()).filter(u -> u.getDeletedAt() == null)
                .map(AppUser::getEmail).orElse(null);
        if (recipient == null) return;                               // account deleted: nobody to tell
        Email email = compose(event, order);
        notifications.save(new Notification(order.getId(), recipient, event.type().name(), email.subject(),
                email.body(), LocalDateTime.now(clock)));
    }

    /** Account e-mails (no order): queued in the same transaction as the account change. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(AccountEmailEvent event) {
        notifications.save(new Notification(null, event.recipient(), event.type().name(), event.subject(),
                event.body(), LocalDateTime.now(clock)));
    }

    /** Plain-text e-mails: readable in every mail client, nothing to render or sanitise. */
    static Email compose(OrderNotificationEvent event, CustomerOrder o) {
        String no = o.getOrderNumber();
        return switch (event.type()) {
            case ORDER_PLACED -> new Email("Order " + no + " received - " + money(o.getTotal()),
                    "Thank you for your order " + no + ".\n\n" + lines(o.getItems())
                            + "\n" + totals(o) + shipsTo(o)
                            + "\nNext step: pay the order in My orders. We reserve the items for you.");
            case PAYMENT_RECEIVED -> new Email("Payment received for order " + no,
                    "We received your payment of " + money(o.getTotal()) + " for order " + no
                            + ".\nWe will e-mail you again when it ships.");
            case ORDER_SHIPPED -> new Email("Order " + no + " is on its way",
                    "Order " + no + " has shipped with " + o.getCarrier() + ".\nTracking number: "
                            + o.getTrackingNumber() + "\nYou can follow it under My orders.");
            case ORDER_DELIVERED -> new Email("Order " + no + " was delivered",
                    "Order " + no + " was delivered. Enjoy!\nSomething wrong? You can request a return within 30 days"
                            + " under My orders.");
            case ORDER_CANCELLED -> new Email("Order " + no + " was cancelled",
                    "Order " + no + " was cancelled." + (event.amount() == null ? ""
                            : "\nWe refunded " + money(event.amount()) + " to your card; it can take a few days to appear."));
            case RETURN_APPROVED -> new Email("Return approved for order " + no,
                    "Your return for order " + no + " was approved.\nRefund: " + money(event.amount())
                            + " to your card; it can take a few days to appear.");
            case RETURN_REJECTED -> new Email("Return for order " + no + " was not approved",
                    "Your return for order " + no + " was not approved. Questions? Reply to this e-mail.");
            case RETURN_LABEL_READY -> new Email("Your return label for order " + no,
                    "A prepaid return label for order " + no + " is ready. Sign in, open the order and choose "
                            + "\"Print return label\". Pack the items, stick the label on the parcel and hand it to the "
                            + "carrier; there is nothing to pay.");
        };
    }

    static String money(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String lines(List<OrderItem> items) {
        return items.stream()
                .map(i -> "  " + i.getQuantity() + " x " + i.getProductName() + "  " + money(i.getLineTotal()))
                .collect(Collectors.joining("\n")) + "\n";
    }

    /** "Ships to: ..." - no phone number in an e-mail; empty for orders placed before addresses existed. */
    private static String shipsTo(CustomerOrder o) {
        return o.getShippingAddress() == null ? "" : "Ships to: " + o.getShippingAddress().oneLine() + "\n";
    }

    private static String totals(CustomerOrder o) {
        StringBuilder s = new StringBuilder("Subtotal " + money(o.getSubtotal()) + "\n");
        if (o.getDiscount().signum() > 0) {
            s.append("Discount -").append(money(o.getDiscount())).append(" (").append(o.getCouponCode()).append(")\n");
        }
        return s.append("Shipping ").append(money(o.getShippingFee())).append("\nTax ").append(money(o.getTax()))
                .append("\nTotal ").append(money(o.getTotal())).append("\n").toString();
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<Notification> list(String status, Long orderId) {
        if (orderId != null) return notifications.findByOrderIdOrderByIdAsc(orderId);
        if (status != null && !status.isBlank()) return notifications.findTop100ByStatusOrderByIdDesc(status.trim().toUpperCase());
        return notifications.findTop100ByOrderByIdDesc();
    }

    /** 409 unless FAILED: a pending or sent e-mail is never sent twice by an admin click. */
    @Transactional
    public void retry(Long id) {
        if (notifications.requeue(id, LocalDateTime.now(clock)) == 0) {
            throw new ConflictException("Only a FAILED notification can be sent again");
        }
    }
}
