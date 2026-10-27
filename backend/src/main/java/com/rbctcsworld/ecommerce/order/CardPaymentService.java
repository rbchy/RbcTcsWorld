package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.payment.PaymentGateway;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.HostedPayment;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.HostedStatus;
import com.rbctcsworld.ecommerce.payment.PaymentService;
import com.rbctcsworld.ecommerce.payment.StripeWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

/**
 * Paying with the hosted card form (Stripe Elements): the card never reaches this server.
 *
 *  1. start:   the customer opens the payment form; this creates (or finds again) the order's PaymentIntent and hands
 *              its client secret to the browser, which shows Stripe's card form and confirms the payment with Stripe.
 *  2. settle:  the order becomes PAID only when STRIPE says the PaymentIntent succeeded - read from Stripe by the
 *              confirm call, or sent by Stripe as a signed webhook. Whichever comes first settles it; the other finds it
 *              settled (payment_intents.settled_at), so an order is never paid twice.
 *
 * Nothing the browser says is trusted: not the status, not the amount. A succeeded payment for another amount than the
 * order's total, or a PaymentIntent that is not the one stored for the order, never pays the order.
 */
@Service
@Transactional
public class CardPaymentService {

    private static final Logger log = LoggerFactory.getLogger(CardPaymentService.class);
    static final String AMOUNT_MISMATCH = "Paid amount does not match the order";
    static final String NOT_STARTED = "No card payment was started for this order";

    private final OrderService orders;
    private final OrderRepository orderRepository;
    private final PaymentService payments;
    private final PaymentGateway gateway;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CardPaymentService(OrderService orders, OrderRepository orderRepository, PaymentService payments,
                              PaymentGateway gateway, JdbcTemplate jdbc, Clock clock) {
        this.orders = orders;
        this.orderRepository = orderRepository;
        this.payments = payments;
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record CardPaymentResponse(String clientSecret, String publishableKey) {
    }

    /** 404 not your order, 409 not payable or no hosted form, 503 provider unreachable; otherwise the client secret. */
    public CardPaymentResponse start(String email, Long orderId) {
        CustomerOrder order = orders.owned(email, orderId);
        order.assertCanMoveTo(OrderStatus.PAID);
        HostedPayment payment = gateway.startHostedPayment(order.getId(), order.getTotal());
        if (!payment.reference().equals(storedIntent(order.getId()))) {
            // first start, or the provider made a new PaymentIntent (e.g. the old one expired): keep only the current one
            jdbc.update("DELETE FROM payment_intents WHERE order_id = ?", order.getId());
            jdbc.update("INSERT INTO payment_intents (order_id, intent_id, amount, created_at) VALUES (?, ?, ?, ?)",
                    order.getId(), payment.reference(), order.getTotal(), now());
        }
        return new CardPaymentResponse(payment.clientSecret(), gateway.publishableKey());
    }

    /**
     * After the browser confirmed the payment: asks Stripe how it ended and settles the order. A payment that is
     * still processing leaves the order PLACED (the webhook will settle it). 404 not your order, 409 nothing started.
     */
    public OrderResponse confirm(String email, Long orderId) {
        CustomerOrder order = orders.owned(email, orderId);
        String intent = storedIntent(order.getId());
        if (intent == null) {
            throw new ConflictException(NOT_STARTED);
        }
        HostedStatus status = gateway.hostedPaymentStatus(intent);
        if (status.succeeded()) {
            settleSucceeded(order, status);
        }
        return OrderResponse.from(order);
    }

    /**
     * A verified Stripe event. Each event id is handled once (Stripe resends until it gets a 2xx). Succeeded pays the
     * order, payment_failed records the failed attempt; events for unknown orders or other intents change nothing.
     */
    public void onEvent(StripeWebhook.Event event) {
        Integer seen = jdbc.queryForObject("SELECT COUNT(*) FROM stripe_events WHERE event_id = ?", Integer.class, event.id());
        if (seen != null && seen > 0) {
            return;
        }
        jdbc.update("INSERT INTO stripe_events (event_id, type, received_at) VALUES (?, ?, ?)", event.id(), event.type(), now());
        HostedStatus payment = event.payment();
        if (payment == null || payment.orderId() == null) {
            return;
        }
        CustomerOrder order = orderRepository.findById(payment.orderId()).orElse(null);
        if (order == null || !payment.reference().equals(storedIntent(order.getId()))) {
            log.warn("Stripe event {} ({}) is not for a payment this shop started; ignored", event.id(), event.type());
            return;
        }
        if ("payment_intent.succeeded".equals(event.type())) {
            settleSucceeded(order, payment);
        } else if ("payment_intent.payment_failed".equals(event.type())) {
            payments.recordHosted(order.getId(), order.getTotal(), false, payment.failureReason(), payment.reference());
        }
    }

    private void settleSucceeded(CustomerOrder order, HostedStatus payment) {
        if (payment.amountCents() != cents(order.getTotal())) {
            log.error("Order {}: Stripe charged {} cents, the order total is {}; not paid", order.getId(), payment.amountCents(), order.getTotal());
            payments.recordHosted(order.getId(), order.getTotal(), false, AMOUNT_MISMATCH, payment.reference());
            return;
        }
        int claimed = jdbc.update("UPDATE payment_intents SET settled_at = ? WHERE order_id = ? AND intent_id = ? AND settled_at IS NULL",
                now(), order.getId(), payment.reference());
        if (claimed == 0) {
            return;   // the confirm call or an earlier event settled it already
        }
        payments.recordHosted(order.getId(), order.getTotal(), true, null, payment.reference());
        if (OrderStatus.PLACED.equals(order.getStatus())) {
            orders.markPaid(order);
        } else {
            // e.g. cancelled while the customer was paying: the money is recorded, so the store can refund it
            log.warn("Order {} was {} when its payment succeeded; payment recorded, order not changed", order.getId(), order.getStatus());
        }
    }

    private String storedIntent(Long orderId) {
        List<String> ids = jdbc.queryForList("SELECT intent_id FROM payment_intents WHERE order_id = ?", String.class, orderId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    static long cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
