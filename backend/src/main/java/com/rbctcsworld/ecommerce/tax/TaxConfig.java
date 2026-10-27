package com.rbctcsworld.ecommerce.tax;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;

/**
 * app.tax.provider = flat (default) | stripe. Stripe Tax uses the same Stripe account and key as payments
 * (STRIPE_SECRET_KEY); like payments it refuses a typo, a missing key, and a live key outside the 'prod' profile.
 */
@Configuration
class TaxConfig {

    @Bean
    TaxCalculator taxCalculator(@Value("${app.tax.provider:flat}") String provider,
                                @Value("${app.pricing.tax-rate}") BigDecimal rate,
                                @Value("${app.payment.stripe.api-base:https://api.stripe.com}") String apiBase,
                                @Value("${app.payment.stripe.secret-key:}") String secretKey,
                                @Value("${app.payment.stripe.currency:usd}") String currency,
                                @Value("${app.payment.stripe.timeout-seconds:10}") long timeoutSeconds,
                                Environment env) {
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        return create(provider, rate, apiBase, secretKey, currency, Duration.ofSeconds(timeoutSeconds), prod);
    }

    static TaxCalculator create(String provider, BigDecimal rate, String apiBase, String secretKey, String currency,
                                Duration timeout, boolean prod) {
        return switch (provider.trim().toLowerCase()) {
            case "flat" -> new FlatRateTaxCalculator(rate);
            case "stripe" -> {
                if (secretKey == null || !secretKey.startsWith("sk_")) {
                    throw new IllegalStateException("app.tax.provider=stripe needs STRIPE_SECRET_KEY (sk_test_...)");
                }
                if (secretKey.startsWith("sk_live_") && !prod) {
                    throw new IllegalStateException("Refusing a LIVE Stripe key outside the 'prod' profile");
                }
                yield new StripeTaxCalculator(new StripeTaxClient(apiBase, secretKey, timeout), rate, currency);
            }
            default -> throw new IllegalStateException("app.tax.provider must be 'flat' or 'stripe', not '" + provider + "'");
        };
    }
}
