package com.rbctcsworld.ecommerce.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Arrays;

/**
 * app.payment.provider = mock (default) | stripe. Fails the start for a typo, a missing key, or a LIVE
 * Stripe key outside the 'prod' profile (a test run must never be able to move real money).
 */
@Configuration
class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(@Value("${app.payment.provider:mock}") String provider,
                                  @Value("${app.payment.stripe.api-base:https://api.stripe.com}") String apiBase,
                                  @Value("${app.payment.stripe.secret-key:}") String secretKey,
                                  @Value("${app.payment.stripe.currency:usd}") String currency,
                                  @Value("${app.payment.stripe.timeout-seconds:10}") long timeoutSeconds,
                                  Environment env) {
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        return create(provider, apiBase, secretKey, currency, Duration.ofSeconds(timeoutSeconds), prod);
    }

    static PaymentGateway create(String provider, String apiBase, String secretKey, String currency,
                                 Duration timeout, boolean prod) {
        return switch (provider.trim().toLowerCase()) {
            case "mock" -> new MockPaymentGateway();
            case "stripe" -> {
                if (secretKey == null || !secretKey.startsWith("sk_")) {
                    throw new IllegalStateException("app.payment.provider=stripe needs STRIPE_SECRET_KEY (sk_test_...)");
                }
                if (secretKey.startsWith("sk_live_") && !prod) {
                    throw new IllegalStateException("Refusing a LIVE Stripe key outside the 'prod' profile");
                }
                yield new StripePaymentGateway(new StripeClient(apiBase, secretKey, timeout), currency);
            }
            default -> throw new IllegalStateException(
                    "app.payment.provider must be 'mock' or 'stripe', not '" + provider + "'");
        };
    }
}
