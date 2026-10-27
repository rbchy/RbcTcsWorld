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
 * Two card forms:
 *  - Stripe Elements (with a publishable key): the browser hands the card to Stripe; this server creates the
 *    PaymentIntent without card data and later reads how it ended. The card number never reaches this server.
 *  - the shop's own form (test mode only): the backend maps Stripe's published test cards to Stripe's ready-made
 *    test PaymentMethods; any other number is refused. With a LIVE key card numbers are refused altogether.
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
    private final String publishableKey;
    private final boolean live;

    StripePaymentGateway(StripeClient stripe, String currency) {
        this(stripe, currency, null, false);
    }

    /** publishableKey (pk_...) enables Stripe Elements; live = a live secret key, which refuses card numbers. */
    StripePaymentGateway(StripeClient stripe, String currency, String publishableKey, boolean live) {
        this.stripe = stripe;
        this.currency = currency;
        this.publishableKey = publishableKey;
        this.live = live;
    }

    @Override
    public boolean refusesCardNumbers() {
        return live;
    }

    @Override
    public String publishableKey() {
        return publishableKey;
    }

    /** The same order and amount give the same PaymentIntent: reloading the payment page never makes a second one. */
    @Override
    public HostedPayment startHostedPayment(Long orderId, BigDecimal amount) {
        if (publishableKey == null) {
            return PaymentGateway.super.startHostedPayment(orderId, amount);
        }
        long cents = cents(amount);
        try {
            StripeClient.HostedIntent intent = stripe.createHostedIntent(cents, currency, orderId,
                    "order-" + orderId + "-elements-" + cents);
            return new HostedPayment(intent.id(), intent.clientSecret());
        } catch (StripeException e) {
            throw unavailable("payment start", orderId, e);
        }
    }

    @Override
    public HostedStatus hostedPaymentStatus(String reference) {
        try {
            StripeClient.IntentState s = stripe.retrieveIntent(reference);
            return new HostedStatus(s.id(), s.status(), s.amount(), orderId(s.orderId()),
                    s.errorCode() == null && s.declineCode() == null ? null : reason(s.declineCode(), s.errorCode()));
        } catch (StripeException e) {
            throw unavailable("status check", null, e);
        }
    }

    /** Our order id from the PaymentIntent's metadata; null when missing or not a number (not one of ours). */
    static Long orderId(String metadata) {
        try {
            return metadata == null ? null : Long.valueOf(metadata);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public GatewayResult charge(Long orderId, String cardNumber, BigDecimal amount) {
        if (live) {   // PaymentService checks refusesCardNumbers first; this is the last line of defence
            return GatewayResult.decline("Enter the card in the secure card form", null);
        }
        String paymentMethod = TEST_PAYMENT_METHODS.get(cardNumber);
        if (paymentMethod == null) {
            return GatewayResult.decline("Use a Stripe test card, e.g. 4242 4242 4242 4242", null);
        }
        return chargeToken(orderId, paymentMethod, amount);
    }

    /** In test mode the "token" of a card is its Stripe test PaymentMethod; live mode would attach a real one. */
    @Override
    public String tokenize(String cardNumber) {
        return live ? null : TEST_PAYMENT_METHODS.get(cardNumber);
    }

    @Override
    public GatewayResult chargeToken(Long orderId, String paymentMethod, BigDecimal amount) {
        if (paymentMethod == null || !paymentMethod.startsWith("pm_")) {
            return GatewayResult.decline("Unknown saved card", null);
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
        return reason(e.declineCode(), e.code());
    }

    /** decline_code wins over code; both may be null. Public for the webhook, which gets them from the event. */
    public static String reason(String declineCode, String errorCode) {
        String code = declineCode != null ? declineCode : errorCode;
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
