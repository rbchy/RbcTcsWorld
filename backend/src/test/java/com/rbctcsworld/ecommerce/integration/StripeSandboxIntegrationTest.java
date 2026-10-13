package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.payment.PaymentGateway;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.GatewayResult;
import com.rbctcsworld.ecommerce.payment.StripeTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behaviour against REAL Stripe test mode (no money moves). Runs only when STRIPE_TEST_SECRET_KEY holds an
 * sk_test_ key (GitHub secret or local environment); skipped otherwise, never fails a build for a missing key.
 */
class StripeSandboxIntegrationTest {

    private static String key;
    private static PaymentGateway stripe;

    @BeforeAll
    static void key() {
        key = System.getenv("STRIPE_TEST_SECRET_KEY");
        assumeTrue(key != null && key.startsWith("sk_test_"), "no Stripe test key (STRIPE_TEST_SECRET_KEY)");
        stripe = StripeTestSupport.gateway("https://api.stripe.com", key);
    }

    private static long orderId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);   // fresh idempotency keys per run
    }

    @Test
    void approvedCardIsChargedAndPartlyRefunded() {
        long order = orderId();
        GatewayResult charge = stripe.charge(order, "4242424242424242", new BigDecimal("24.50"));
        assertThat(charge.approved()).isTrue();
        assertThat(charge.reference()).startsWith("pi_");

        GatewayResult refund = stripe.refund(order, charge.reference(), new BigDecimal("10.00"), 1);
        assertThat(refund.approved()).isTrue();
        assertThat(refund.reference()).startsWith("re_");
    }

    @Test
    void declinedAndInsufficientFundsCardsGiveTheSameReasonsAsTheMock() {
        assertThat(stripe.charge(orderId(), "4000000000000002", new BigDecimal("5.00")).reason()).isEqualTo("Card declined");
        assertThat(stripe.charge(orderId(), "4000000000009995", new BigDecimal("5.00")).reason()).isEqualTo("Insufficient funds");
    }

    @Test
    void aRetryWithTheSameKeyDoesNotChargeTwice() {
        long order = orderId();
        GatewayResult first = stripe.charge(order, "4242424242424242", new BigDecimal("7.00"));
        GatewayResult retry = stripe.charge(order, "4242424242424242", new BigDecimal("7.00"));
        assertThat(retry.reference()).isEqualTo(first.reference());
    }
}
