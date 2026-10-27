package com.rbctcsworld.ecommerce.tax;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which tax provider starts, and the start refusals (typo, no key, live key outside prod). */
class TaxConfigTest {

    private static final BigDecimal RATE = new BigDecimal("0.06");

    private static TaxCalculator create(String provider, String key, boolean prod) {
        return TaxConfig.create(provider, RATE, "https://api.stripe.com", key, "usd", Duration.ofSeconds(5), prod);
    }

    @Test
    void flatIsTheDefaultAndNeedsNoKey() {
        assertThat(create(" Flat ", null, false)).isInstanceOf(FlatRateTaxCalculator.class);
    }

    @Test
    void stripeNeedsASecretKey() {
        assertThat(create("stripe", "sk_test_1", false)).isInstanceOf(StripeTaxCalculator.class);
        assertThatThrownBy(() -> create("stripe", "", false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
        assertThatThrownBy(() -> create("stripe", null, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> create("stripe", "pk_test_1", false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aLiveKeyIsOnlyAcceptedInProd() {
        assertThatThrownBy(() -> create("stripe", "sk_live_1", false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE");
        assertThat(create("stripe", "sk_live_1", true)).isInstanceOf(StripeTaxCalculator.class);
    }

    @Test
    void aTypoStopsTheStart() {
        assertThatThrownBy(() -> create("avalara", null, false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'avalara'");
    }

    @Test
    void theSpringBeanReadsTheProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThat(new TaxConfig().taxCalculator("stripe", RATE, "https://api.stripe.com", "sk_live_1", "usd", 5, prod))
                .isInstanceOf(StripeTaxCalculator.class);
        assertThatThrownBy(() -> new TaxConfig().taxCalculator("stripe", RATE, "https://api.stripe.com", "sk_live_1", "usd", 5,
                new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }
}
