package com.rbctcsworld.ecommerce.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stand-in for a real payment provider (Stripe/Adyen style test cards). Deterministic, so tests can
 * trigger every outcome on purpose:
 *
 *   4000 0000 0000 0002  -> declined            ("Card declined")
 *   4000 0000 0000 9995  -> declined            ("Insufficient funds")
 *   any other Luhn-valid -> approved            (e.g. 4242 4242 4242 4242)
 *
 * Card format, Luhn and expiry are validated BEFORE the gateway is called (PaymentService).
 * Default provider (app.payment.provider=mock, see PaymentGatewayConfig); StripePaymentGateway is the real one.
 */
public class MockPaymentGateway implements PaymentGateway {

    public static final String DECLINED_CARD = "4000000000000002";
    public static final String INSUFFICIENT_FUNDS_CARD = "4000000000009995";

    @Override
    public GatewayResult charge(Long orderId, String cardNumber, BigDecimal amount) {
        return switch (cardNumber) {
            case DECLINED_CARD -> GatewayResult.decline("Card declined", null);
            case INSUFFICIENT_FUNDS_CARD -> GatewayResult.decline("Insufficient funds", null);
            default -> GatewayResult.ok("mock_ch_" + UUID.randomUUID());
        };
    }

    /** The token only remembers which kind of test card it was, so a saved declining card keeps declining. */
    @Override
    public String tokenize(String cardNumber) {
        String kind = switch (cardNumber) {
            case DECLINED_CARD -> "declined";
            case INSUFFICIENT_FUNDS_CARD -> "funds";
            default -> "ok";
        };
        return "mock_pm_" + kind + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public GatewayResult chargeToken(Long orderId, String token, BigDecimal amount) {
        if (token.startsWith("mock_pm_declined_")) return GatewayResult.decline("Card declined", null);
        if (token.startsWith("mock_pm_funds_")) return GatewayResult.decline("Insufficient funds", null);
        if (token.startsWith("mock_pm_ok_")) return GatewayResult.ok("mock_ch_" + UUID.randomUUID());
        return GatewayResult.decline("Unknown saved card", null);
    }

    @Override
    public GatewayResult refund(Long orderId, String chargeReference, BigDecimal amount, int refundNumber) {
        return GatewayResult.ok("mock_re_" + UUID.randomUUID());
    }

    @Override
    public String name() {
        return "mock";
    }

    /** Luhn (mod 10) checksum used by every real card number. */
    public static boolean luhnValid(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
