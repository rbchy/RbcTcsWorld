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
 * app.payment.stripe.publishable-key (pk_...) turns on Stripe Elements; it must be from the same mode (test or
 * live) as the secret key, and a live secret key needs it, because a live key refuses card numbers.
 */
@Configuration
class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(@Value("${app.payment.provider:mock}") String provider,
                                  @Value("${app.payment.stripe.api-base:https://api.stripe.com}") String apiBase,
                                  @Value("${app.payment.stripe.secret-key:}") String secretKey,
                                  @Value("${app.payment.stripe.currency:usd}") String currency,
                                  @Value("${app.payment.stripe.timeout-seconds:10}") long timeoutSeconds,
                                  @Value("${app.payment.stripe.publishable-key:}") String publishableKey,
                                  Environment env) {
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        return create(provider, apiBase, secretKey, currency, Duration.ofSeconds(timeoutSeconds), prod, publishableKey);
    }

    static PaymentGateway create(String provider, String apiBase, String secretKey, String currency,
                                 Duration timeout, boolean prod) {
        return create(provider, apiBase, secretKey, currency, timeout, prod, null);
    }

    static PaymentGateway create(String provider, String apiBase, String secretKey, String currency,
                                 Duration timeout, boolean prod, String publishableKey) {
        return switch (provider.trim().toLowerCase()) {
            case "mock" -> new MockPaymentGateway();
            case "stripe" -> {
                if (secretKey == null || !secretKey.startsWith("sk_")) {
                    throw new IllegalStateException("app.payment.provider=stripe needs STRIPE_SECRET_KEY (sk_test_...)");
                }
                if (secretKey.startsWith("sk_live_") && !prod) {
                    throw new IllegalStateException("Refusing a LIVE Stripe key outside the 'prod' profile");
                }
                boolean live = secretKey.startsWith("sk_live_");
                String pk = publishableKey == null || publishableKey.isBlank() ? null : publishableKey.trim();
                if (pk != null && !pk.startsWith(live ? "pk_live_" : "pk_test_")) {
                    throw new IllegalStateException("STRIPE_PUBLISHABLE_KEY must be a " + (live ? "pk_live_" : "pk_test_")
                            + "... key from the same Stripe mode as the secret key");
                }
                if (live && pk == null) {
                    throw new IllegalStateException("A live Stripe key needs STRIPE_PUBLISHABLE_KEY: card numbers are refused in live mode");
                }
                yield new StripePaymentGateway(new StripeClient(apiBase, secretKey, timeout), currency, pk, live);
            }
            default -> throw new IllegalStateException(
                    "app.payment.provider must be 'mock' or 'stripe', not '" + provider + "'");
        };
    }
}
