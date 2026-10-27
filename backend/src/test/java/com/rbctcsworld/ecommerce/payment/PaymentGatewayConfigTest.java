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
        assertThatCode(() -> PaymentGatewayConfig.create("stripe", "http://localhost:12111", "sk_live_123", "usd",
                Duration.ofSeconds(5), true, "pk_live_123")).doesNotThrowAnyException();
    }

    private static PaymentGateway withPublishable(String secret, String publishable, boolean prod) {
        return PaymentGatewayConfig.create("stripe", "http://localhost:12111", secret, "usd", Duration.ofSeconds(5), prod, publishable);
    }

    @Test
    void aPublishableKeyTurnsOnStripeElements() {
        assertThat(withPublishable("sk_test_1", " pk_test_1 ", false).publishableKey()).isEqualTo("pk_test_1");
        assertThat(withPublishable("sk_test_1", "", false).publishableKey()).isNull();      // blank = the shop's own form
        assertThat(withPublishable("sk_test_1", null, false).publishableKey()).isNull();
    }

    @Test
    void thePublishableKeyMustBeFromTheSameModeAsTheSecretKey() {
        assertThatThrownBy(() -> withPublishable("sk_test_1", "pk_live_1", false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pk_test_");
        assertThatThrownBy(() -> withPublishable("sk_live_1", "pk_test_1", true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pk_live_");
        assertThatThrownBy(() -> withPublishable("sk_test_1", "sk_test_2", false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aLiveKeyNeedsTheSecureCardFormAndRefusesCardNumbers() {
        assertThatThrownBy(() -> withPublishable("sk_live_1", null, true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_PUBLISHABLE_KEY");
        PaymentGateway live = withPublishable("sk_live_1", "pk_live_1", true);
        assertThat(live.acceptsCardNumbers()).isFalse();
        assertThat(withPublishable("sk_test_1", "pk_test_1", false).acceptsCardNumbers()).isTrue();
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
        assertThat(config.paymentGateway("stripe", "http://localhost:12111", "sk_live_1", "usd", 5, "pk_live_1", prod))
                .isInstanceOf(StripePaymentGateway.class);
        assertThatThrownBy(() -> config.paymentGateway("stripe", "http://localhost:12111", "sk_live_1", "usd", 5, "pk_live_1", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}
