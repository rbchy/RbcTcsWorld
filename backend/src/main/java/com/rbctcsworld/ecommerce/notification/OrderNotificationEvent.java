package com.rbctcsworld.ecommerce.notification;

import java.math.BigDecimal;

/**
 * Published by the order, fulfilment and return services inside their transaction. NotificationService
 * turns it into an outbox row in that same transaction. amount = refund (cancel, return), else null.
 */
public record OrderNotificationEvent(Long orderId, Type type, BigDecimal amount) {

    public enum Type {
        ORDER_PLACED, PAYMENT_RECEIVED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED, RETURN_APPROVED, RETURN_REJECTED,
        /** A prepaid return label was bought (ADR-0023). */
        RETURN_LABEL_READY
    }

    public static OrderNotificationEvent of(Long orderId, Type type) {
        return new OrderNotificationEvent(orderId, type, null);
    }
}
