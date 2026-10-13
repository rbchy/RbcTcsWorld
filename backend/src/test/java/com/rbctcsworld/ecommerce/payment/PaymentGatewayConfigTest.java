package com.rbctcsworld.ecommerce.payment;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayConfigTest {

    private static PaymentGateway create(String provider, String key, boolean prod) {
        return PaymentGatewayConfig.create(provider, "http://localhost:12111", key, "usd", Duration.ofSeconds(5), prod);
    }

    @Test
    void mockIsTheDefaultProvider() {
        assertThat(create("mock", "", false)).isInstanceOf(MockPaymentGateway.class);
    }

    @Test
    void stripeWithATestKey() {
        assertThat(create(" Stripe ", "sk_test_123", false)).isInstanceOf(StripePaymentGateway.class);
    }

    @Test
    void stripeWithoutAKeyStopsTheStart() {
        assertThatThrownBy(() -> create("stripe", "", false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
        assertThatThrownBy(() -> create("stripe", null, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> create("stripe", "pk_test_123", false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aLiveKeyOnlyRunsWithTheProdProfile() {
        assertThatThrownBy(() -> create("stripe", "sk_live_123", false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE");
        assertThatCode(() -> create("stripe", "sk_live_123", true)).doesNotThrowAnyException();
    }

    @Test
    void aTypoStopsTheStart() {
        assertThatThrownBy(() -> create("strip", "sk_test_123", false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'strip'");
    }

    @Test
    void springBeanMethodReadsTheProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        PaymentGatewayConfig config = new PaymentGatewayConfig();
        assertThat(config.paymentGateway("stripe", "http://localhost:12111", "sk_live_1", "usd", 5, prod))
                .isInstanceOf(StripePaymentGateway.class);
        assertThatThrownBy(() -> config.paymentGateway("stripe", "http://localhost:12111", "sk_live_1", "usd", 5, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}
