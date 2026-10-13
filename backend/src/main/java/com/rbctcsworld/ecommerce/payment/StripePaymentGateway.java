package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Payments through Stripe (PaymentIntents + Refunds), app.payment.provider=stripe.
 *
 * Card numbers never go to Stripe: in a real shop the browser hands the card to Stripe (Stripe Elements)
 * and the backend only sees a PaymentMethod id. This demo keeps its own card form, so the backend maps
 * Stripe's published test cards to Stripe's ready-made test PaymentMethods. Any other number is refused.
 *
 * Outcomes:
 *  - status "succeeded"        -> approved, the PaymentIntent id is kept for refunds
 *  - card_error (402 from Stripe) -> declined with a short reason, same texts as the mock gateway
 *  - anything else (network, 5xx, bad key, invalid request) -> PaymentUnavailableException (HTTP 503)
 */
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    /** Stripe test card -> Stripe test PaymentMethod (https://docs.stripe.com/testing). */
    static final Map<String, String> TEST_PAYMENT_METHODS = Map.of(
            "4242424242424242", "pm_card_visa",
            "5555555555554444", "pm_card_mastercard",
            MockPaymentGateway.DECLINED_CARD, "pm_card_chargeDeclined",
            MockPaymentGateway.INSUFFICIENT_FUNDS_CARD, "pm_card_chargeDeclinedInsufficientFunds",
            "4000000000000069", "pm_card_chargeDeclinedExpiredCard",
            "4000000000000127", "pm_card_chargeDeclinedIncorrectCvc");

    static final String UNAVAILABLE = "Payment service is not available right now. Please try again in a moment.";

    private final StripeClient stripe;
    private final String currency;

    StripePaymentGateway(StripeClient stripe, String currency) {
        this.stripe = stripe;
        this.currency = currency;
    }

    @Override
    public GatewayResult charge(Long orderId, String cardNumber, BigDecimal amount) {
        String paymentMethod = TEST_PAYMENT_METHODS.get(cardNumber);
        if (paymentMethod == null) {
            return GatewayResult.decline("Use a Stripe test card, e.g. 4242 4242 4242 4242", null);
        }
        long cents = cents(amount);
        // same order + amount + card = same key: a retry after a timeout returns the first answer, no double charge
        String key = "order-" + orderId + "-charge-" + cents + "-" + paymentMethod;
        try {
            StripeClient.PaymentIntent intent = stripe.createPaymentIntent(cents, currency, paymentMethod, orderId, key);
            return switch (intent.status()) {
                case "succeeded" -> GatewayResult.ok(intent.id());
                case "requires_action" -> GatewayResult.decline("Card needs authentication (3-D Secure)", intent.id());
                default -> GatewayResult.decline("Payment not completed (" + intent.status() + ")", intent.id());
            };
        } catch (StripeException e) {
            if (e.isCardError()) {
                return GatewayResult.decline(reason(e), e.paymentIntentId());
            }
            throw unavailable("charge", orderId, e);
        }
    }

    @Override
    public GatewayResult refund(Long orderId, String chargeReference, BigDecimal amount, int refundNumber) {
        if (chargeReference == null || !chargeReference.startsWith("pi_")) {
            throw new PaymentUnavailableException("This payment was not made through Stripe and cannot be refunded automatically.");
        }
        long cents = cents(amount);
        String key = "order-" + orderId + "-refund-" + refundNumber + "-" + cents;
        try {
            StripeClient.Refund refund = stripe.createRefund(chargeReference, cents, orderId, key);
            return switch (refund.status()) {
                case "succeeded", "pending" -> GatewayResult.ok(refund.id());     // card refunds may settle later
                default -> GatewayResult.decline("Refund " + refund.status(), refund.id());
            };
        } catch (StripeException e) {
            throw unavailable("refund", orderId, e);
        }
    }

    @Override
    public String name() {
        return "stripe";
    }

    /** Stripe wants whole cents: 24.50 -> 2450 (stripe-mock rejects "24.50"). */
    static long cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    /** Short, customer-safe reasons - the same texts as the mock gateway for the same test cards. */
    static String reason(StripeException e) {
        String code = e.declineCode() != null ? e.declineCode() : e.code();
        if (code == null) return "Card declined";
        return switch (code) {
            case "insufficient_funds" -> "Insufficient funds";
            case "expired_card" -> "Card has expired";
            case "incorrect_cvc" -> "Incorrect CVC";
            default -> "Card declined";
        };
    }

    private static PaymentUnavailableException unavailable(String action, Long orderId, StripeException e) {
        // the message may help support; it never contains card data or the API key
        log.error("Stripe {} for order {} failed: HTTP {} {} {}", action, orderId, e.status(), e.type(), e.getMessage());
        return new PaymentUnavailableException(UNAVAILABLE);
    }
}
