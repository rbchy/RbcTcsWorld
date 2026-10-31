package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import com.rbctcsworld.ecommerce.tax.TaxCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.Executor;

/**
 * Keeps the tax provider's report in step with the money (ADR-0012):
 *  - paid (and committed): the order's tax calculation is recorded as a sale; its id is kept on the order;
 *  - refunded (cancelled after payment, or a return approved): the sale is reversed, in full when the whole order
 *    total went back, otherwise by the refunded amount, so the shop does not file tax it gave back.
 * Never fails the payment or the refund: the money has moved, so a provider outage is logged with the order number
 * for a manual retry (the same order number / reversal reference is the same idempotency key).
 * The work runs on the application's task executor in its own transaction: right after the commit the request thread
 * still holds its database connection, and asking for a second one there could starve the pool under load (seen in the
 * flash-sale test). The request also does not wait for Stripe.
 */
@Component
class TaxSaleRecorder {

    private static final Logger log = LoggerFactory.getLogger(TaxSaleRecorder.class);

    private final OrderRepository orders;
    private final TaxCalculator taxes;
    private final TransactionTemplate tx;
    private final Executor executor;

    TaxSaleRecorder(OrderRepository orders, TaxCalculator taxes, PlatformTransactionManager transactions,
                    @Qualifier("applicationTaskExecutor") Executor executor) {
        this.orders = orders;
        this.taxes = taxes;
        this.tx = new TransactionTemplate(transactions);
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPaid(OrderNotificationEvent event) {
        boolean refund = event.amount() != null && event.amount().signum() > 0;
        boolean relevant = switch (event.type()) {
            case PAYMENT_RECEIVED -> true;
            case ORDER_CANCELLED, RETURN_APPROVED -> refund;
            default -> false;
        };
        if (relevant) {
            executor.execute(() -> tx.executeWithoutResult(status -> handle(event)));
        }
    }

    void handle(OrderNotificationEvent event) {
        switch (event.type()) {
            case PAYMENT_RECEIVED -> recordSale(event.orderId());
            case ORDER_CANCELLED -> reverse(event.orderId(), event.amount(), "-cancel");
            case RETURN_APPROVED -> reverse(event.orderId(), event.amount(), "-return");
            default -> { }
        }
    }

    private void recordSale(Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElse(null);
        if (order == null || order.getTaxCalculationId() == null) {
            return;   // flat rate: nothing to record
        }
        try {
            String transactionId = taxes.record(order.getTaxCalculationId(), order.getOrderNumber());
            if (transactionId != null) {
                order.recordTaxTransaction(transactionId);
                orders.save(order);
            }
        } catch (RuntimeException e) {
            log.error("Order {}: tax sale not recorded with {} ({}); record it again with the same order number",
                    order.getOrderNumber(), taxes.name(), e.getMessage());
        }
    }

    /** refund null or zero: nothing went back (an unpaid order cancelled, or a return refunded with nothing). */
    private void reverse(Long orderId, BigDecimal refund, String suffix) {
        if (refund == null || refund.signum() <= 0) {
            return;
        }
        CustomerOrder order = orders.findById(orderId).orElse(null);
        if (order == null || order.getTaxCalculationId() == null || order.getTaxReversalId() != null) {
            return;   // flat rate, or already reversed
        }
        if (order.getTaxTransactionId() == null) {
            log.error("Order {}: refunded {} but its tax sale was never recorded with {}; record it, then reverse it",
                    order.getOrderNumber(), refund, taxes.name());
            return;
        }
        boolean full = refund.compareTo(order.getTotal()) >= 0;
        String reference = order.getOrderNumber() + suffix;
        try {
            String reversalId = taxes.reverse(order.getTaxTransactionId(), reference, refund, full);
            if (reversalId != null) {
                order.recordTaxReversal(reversalId);
                orders.save(order);
            }
        } catch (RuntimeException e) {
            log.error("Order {}: tax not reversed with {} after a refund of {} ({}); reverse it again as {}",
                    order.getOrderNumber(), taxes.name(), refund, e.getMessage(), reference);
        }
    }
}
