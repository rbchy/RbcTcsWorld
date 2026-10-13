package com.rbctcsworld.ecommerce.payment;

import java.math.BigDecimal;

/**
 * A payment provider. app.payment.provider selects it: "mock" (default, deterministic test cards) or
 * "stripe" (Stripe PaymentIntents and Refunds API, test mode or stripe-mock).
 */
public interface PaymentGateway {

    /** Charges the card. Declines come back as a result; an unreachable provider throws PaymentUnavailableException. */
    GatewayResult charge(Long orderId, String cardNumber, BigDecimal amount);

    /**
     * Refunds (part of) an earlier successful charge, identified by the provider's reference.
     * refundNumber = 1 for the first refund of the order, 2 for the second...: part of the idempotency key,
     * so a retried refund is not paid twice while a second, real refund still goes through.
     */
    GatewayResult refund(Long orderId, String chargeReference, BigDecimal amount, int refundNumber);

    /** Short name stored with every transaction: mock | stripe. */
    String name();

    /** reference = the provider's id (Stripe: pi_... / re_...); kept for refunds and support, never shown to customers. */
    record GatewayResult(boolean approved, String reason, String reference) {
        public static GatewayResult ok(String reference) {
            return new GatewayResult(true, null, reference);
        }

        public static GatewayResult decline(String reason, String reference) {
            return new GatewayResult(false, reason, reference);
        }
    }
}
