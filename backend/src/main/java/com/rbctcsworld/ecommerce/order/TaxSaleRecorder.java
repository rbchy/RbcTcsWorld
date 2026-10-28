package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import com.rbctcsworld.ecommerce.tax.TaxCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Once an order is paid (and the payment committed), records its tax calculation as a sale with the tax provider,
 * so the tax report for filing contains it. Never fails the payment: the money has moved, so a provider outage is
 * logged with the order number for a manual retry (the same order number is the same idempotency key).
 */
@Component
class TaxSaleRecorder {

    private static final Logger log = LoggerFactory.getLogger(TaxSaleRecorder.class);

    private final OrderRepository orders;
    private final TaxCalculator taxes;

    TaxSaleRecorder(OrderRepository orders, TaxCalculator taxes) {
        this.orders = orders;
        this.taxes = taxes;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPaid(OrderNotificationEvent event) {
        if (event.type() != OrderNotificationEvent.Type.PAYMENT_RECEIVED) {
            return;
        }
        CustomerOrder order = orders.findById(event.orderId()).orElse(null);
        if (order == null || order.getTaxCalculationId() == null) {
            return;   // flat rate: nothing to record
        }
        try {
            taxes.record(order.getTaxCalculationId(), order.getOrderNumber());
        } catch (RuntimeException e) {
            log.error("Order {}: tax sale not recorded with {} ({}); record it again with the same order number",
                    order.getOrderNumber(), taxes.name(), e.getMessage());
        }
    }
}
