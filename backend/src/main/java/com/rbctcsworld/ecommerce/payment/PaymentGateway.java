package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;

import java.math.BigDecimal;

/**
 * A payment provider. app.payment.provider selects it: "mock" (default, deterministic test cards) or
 * "stripe" (Stripe PaymentIntents and Refunds API, test mode or stripe-mock).
 *
 * Two ways to take a card:
 *  - the shop's own card form: the number reaches this server and {@link #charge} sends it on (mock, Stripe test mode);
 *  - a hosted card form (Stripe Elements): the browser gives the card to the provider, this server only creates the
 *    payment ({@link #startHostedPayment}) and later asks how it ended ({@link #hostedPaymentStatus}). The card
 *    number never reaches this server. A live Stripe key allows only this way ({@link #refusesCardNumbers}).
 */
public interface PaymentGateway {

    String HOSTED_NOT_AVAILABLE = "The secure card form is not available with this payment provider";

    /** Charges the card. Declines come back as a result; an unreachable provider throws PaymentUnavailableException. */
    GatewayResult charge(Long orderId, String cardNumber, BigDecimal amount);

    /**
     * Turns a card into the provider's own token for later payments, so the card number never has to be kept.
     * Returns null when the provider cannot use this card (Stripe test mode: only its test cards).
     */
    String tokenize(String cardNumber);

    /** Charges a token made by {@link #tokenize}. Same results as {@link #charge}. */
    GatewayResult chargeToken(Long orderId, String token, BigDecimal amount);

    /**
     * Refunds (part of) an earlier successful charge, identified by the provider's reference.
     * refundNumber = 1 for the first refund of the order, 2 for the second...: part of the idempotency key,
     * so a retried refund is not paid twice while a second, real refund still goes through.
     */
    GatewayResult refund(Long orderId, String chargeReference, BigDecimal amount, int refundNumber);

    /** Short name stored with every transaction: mock | stripe. */
    String name();

    /** True when card numbers must not reach this server (a live Stripe key): only the hosted card form may be used. */
    default boolean refusesCardNumbers() {
        return false;
    }

    /** The provider's public key for the hosted card form (Stripe pk_...), or null when there is no hosted form. */
    default String publishableKey() {
        return null;
    }

    /**
     * Creates the payment for the hosted card form, without any card data. Same order and amount give the same
     * payment (idempotent), so reloading the page never creates a second one. 409 without a hosted form.
     */
    default HostedPayment startHostedPayment(Long orderId, BigDecimal amount) {
        throw new ConflictException(HOSTED_NOT_AVAILABLE);
    }

    /** How a hosted payment ended, read from the provider (never from the browser). 409 without a hosted form. */
    default HostedStatus hostedPaymentStatus(String reference) {
        throw new ConflictException(HOSTED_NOT_AVAILABLE);
    }

    /** reference = the provider's payment id (pi_...); clientSecret lets only this browser confirm it. */
    record HostedPayment(String reference, String clientSecret) {
    }

    /**
     * reference = pi_..., status = the provider's word (succeeded, processing, requires_payment_method...),
     * amountCents = what the provider will charge, orderId = from the payment's metadata,
     * failureReason = short customer-safe reason of the last failed attempt, or null.
     */
    record HostedStatus(String reference, String status, long amountCents, Long orderId, String failureReason) {
        public boolean succeeded() {
            return "succeeded".equals(status);
        }
    }

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
