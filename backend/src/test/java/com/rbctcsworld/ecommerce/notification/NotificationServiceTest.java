package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type;
import com.rbctcsworld.ecommerce.order.CustomerOrder;
import com.rbctcsworld.ecommerce.order.OrderItem;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    @Mock NotificationRepository notifications;
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    private NotificationService service;
    private CustomerOrder order;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, orders, users, CLOCK);
        order = new CustomerOrder("ORD-20261012-ABCDEF", 7L);
        ReflectionTestUtils.setField(order, "id", 55L);
        order.addItem(new OrderItem(1L, "SKU-M", "Wireless Mouse", new BigDecimal("12.50"), 2));
        order.applyPricing(new BigDecimal("2.50"), new BigDecimal("5.99"), new BigDecimal("1.35"), new BigDecimal("29.84"), "WELCOME10");
    }

    private NotificationService.Email mail(Type type, BigDecimal amount) {
        return NotificationService.compose(new OrderNotificationEvent(55L, type, amount), order);
    }

    @Test
    void orderPlacedListsItemsAndTheWholePriceBreakdown() {
        NotificationService.Email m = mail(Type.ORDER_PLACED, null);
        assertThat(m.subject()).isEqualTo("Order ORD-20261012-ABCDEF received - $29.84");
        assertThat(m.body()).contains("2 x Wireless Mouse  $25.00", "Subtotal $25.00", "Discount -$2.50 (WELCOME10)",
                "Shipping $5.99", "Tax $1.35", "Total $29.84", "pay the order");
    }

    @Test
    void orderPlacedSaysWhereItShipsButNotThePhoneNumber() {
        order.shipTo(new com.rbctcsworld.ecommerce.order.ShippingAddress("Ada Lovelace", "12 Main St", null,
                "Pittsburgh", "PA", "15213", "412-555-0100"));
        assertThat(mail(Type.ORDER_PLACED, null).body())
                .contains("Ships to: Ada Lovelace, 12 Main St, Pittsburgh, PA 15213\n")
                .doesNotContain("412-555-0100");
    }

    @Test
    void anOrderFromBeforeAddressesHasNoShipsToLine() {
        assertThat(mail(Type.ORDER_PLACED, null).body()).doesNotContain("Ships to");
    }

    @Test
    void noDiscountLineWithoutACoupon() {
        order.applyPricing(BigDecimal.ZERO, new BigDecimal("5.99"), new BigDecimal("1.50"), new BigDecimal("32.49"), null);
        assertThat(mail(Type.ORDER_PLACED, null).body()).doesNotContain("Discount").contains("Total $32.49");
    }

    @Test
    void anAccountEmailIsQueuedWithoutAnOrder() {
        service.on(new AccountEmailEvent("ada@test.com", AccountEmailEvent.Type.PASSWORD_RESET, "Reset", "link"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getOrderId()).isNull();
        assertThat(n.getRecipient()).isEqualTo("ada@test.com");
        assertThat(n.getType()).isEqualTo("PASSWORD_RESET");
        assertThat(n.getSubject()).isEqualTo("Reset");
        assertThat(n.getBody()).isEqualTo("link");
        assertThat(n.getStatus()).isEqualTo(Notification.PENDING);
        verifyNoInteractions(orders, users);
    }

    @Test
    void paymentReceived() {
        NotificationService.Email m = mail(Type.PAYMENT_RECEIVED, null);
        assertThat(m.subject()).isEqualTo("Payment received for order ORD-20261012-ABCDEF");
        assertThat(m.body()).contains("$29.84");
    }

    @Test
    void shippedNamesCarrierAndTrackingNumber() {
        ReflectionTestUtils.setField(order, "carrier", "UPS");
        ReflectionTestUtils.setField(order, "trackingNumber", "UPS-123456789012");
        NotificationService.Email m = mail(Type.ORDER_SHIPPED, null);
        assertThat(m.subject()).isEqualTo("Order ORD-20261012-ABCDEF is on its way");
        assertThat(m.body()).contains("with UPS", "Tracking number: UPS-123456789012");
    }

    @Test
    void deliveredMentionsTheReturnWindow() {
        NotificationService.Email m = mail(Type.ORDER_DELIVERED, null);
        assertThat(m.subject()).isEqualTo("Order ORD-20261012-ABCDEF was delivered");
        assertThat(m.body()).contains("30 days");
    }

    @Test
    void cancelledWithAndWithoutRefund() {
        assertThat(mail(Type.ORDER_CANCELLED, new BigDecimal("29.84")).body()).contains("We refunded $29.84");
        assertThat(mail(Type.ORDER_CANCELLED, null).body()).doesNotContain("refund").contains("was cancelled");
        assertThat(mail(Type.ORDER_CANCELLED, null).subject()).isEqualTo("Order ORD-20261012-ABCDEF was cancelled");
    }

    @Test
    void returnApprovedStatesTheRefund() {
        NotificationService.Email m = mail(Type.RETURN_APPROVED, new BigDecimal("21.2"));
        assertThat(m.subject()).isEqualTo("Return approved for order ORD-20261012-ABCDEF");
        assertThat(m.body()).contains("Refund: $21.20");
    }

    @Test
    void returnRejected() {
        assertThat(mail(Type.RETURN_REJECTED, null).subject()).isEqualTo("Return for order ORD-20261012-ABCDEF was not approved");
    }

    @Test
    void moneyAlwaysHasTwoDecimals() {
        assertThat(NotificationService.money(new BigDecimal("5"))).isEqualTo("$5.00");
        assertThat(NotificationService.money(new BigDecimal("0.005"))).isEqualTo("$0.01");
        assertThat(NotificationService.money(new BigDecimal("1234.5"))).isEqualTo("$1234.50");
    }

    @Test
    void eventBecomesAPendingOutboxRowForTheOrdersCustomer() {
        when(orders.findById(55L)).thenReturn(Optional.of(order));
        when(users.findById(7L)).thenReturn(Optional.of(new AppUser("buyer@test.com", "x")));

        service.on(OrderNotificationEvent.of(55L, Type.PAYMENT_RECEIVED));

        ArgumentCaptor<Notification> row = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(row.capture());
        Notification n = row.getValue();
        assertThat(n.getRecipient()).isEqualTo("buyer@test.com");
        assertThat(n.getOrderId()).isEqualTo(55L);
        assertThat(n.getType()).isEqualTo("PAYMENT_RECEIVED");
        assertThat(n.getSubject()).isEqualTo("Payment received for order ORD-20261012-ABCDEF");
        assertThat(n.getStatus()).isEqualTo(Notification.PENDING);
        assertThat(n.getAttempts()).isZero();
        assertThat(n.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(n.getNextAttemptAt()).isEqualTo(LocalDateTime.now(CLOCK));   // due at once
        assertThat(n.getSentAt()).isNull();
    }

    @Test
    void deletedAccountGetsNoEmail() {
        when(orders.findById(55L)).thenReturn(Optional.of(order));
        when(users.findById(7L)).thenReturn(Optional.empty());
        service.on(OrderNotificationEvent.of(55L, Type.ORDER_SHIPPED));
        verify(notifications, never()).save(any());
    }

    @Test
    void adminListByOrderByStatusOrLatest() {
        Notification n = new Notification(55L, "a@test.com", "ORDER_PLACED", "s", "b", LocalDateTime.now(CLOCK));
        when(notifications.findByOrderIdOrderByIdAsc(55L)).thenReturn(List.of(n));
        when(notifications.findTop100ByStatusOrderByIdDesc("FAILED")).thenReturn(List.of(n, n));
        when(notifications.findTop100ByOrderByIdDesc()).thenReturn(List.of(n, n, n));

        assertThat(service.list("ignored", 55L)).hasSize(1);
        assertThat(service.list(" failed ", null)).hasSize(2);
        assertThat(service.list("  ", null)).hasSize(3);             // blank filter = latest of every status
        assertThat(service.list(null, null)).hasSize(3);
    }

    @Test
    void onlyFailedNotificationsCanBeSentAgain() {
        when(notifications.requeue(1L, LocalDateTime.now(CLOCK))).thenReturn(1);
        when(notifications.requeue(2L, LocalDateTime.now(CLOCK))).thenReturn(0);

        service.retry(1L);
        assertThatThrownBy(() -> service.retry(2L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void composingNeedsNoDatabase() {
        mail(Type.ORDER_DELIVERED, null);
        verifyNoInteractions(notifications, orders, users);
    }
}
