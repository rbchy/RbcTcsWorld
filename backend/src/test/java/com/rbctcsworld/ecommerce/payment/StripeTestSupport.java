package com.rbctcsworld.ecommerce.payment;

import java.time.Duration;

/** Lets the integration tests (other package) use the package-private Stripe client exactly as the shop does. */
public final class StripeTestSupport {

    private StripeTestSupport() {
    }

    public record Intent(String id, String status, long amount, String currency) {
    }

    public record Refund(String id, String status, long amount) {
    }

    public static PaymentGateway elementsGateway(String baseUrl, String secretKey) {
        return PaymentGatewayConfig.create("stripe", baseUrl, secretKey, "usd", Duration.ofSeconds(20), false, "pk_test_contract");
    }

        public static PaymentGateway gateway(String baseUrl, String secretKey) {
        return PaymentGatewayConfig.create("stripe", baseUrl, secretKey, "usd", Duration.ofSeconds(20), false);
    }

    public static Intent createIntent(String baseUrl, String secretKey, long cents, String paymentMethod, Long orderId) {
        StripeClient.PaymentIntent pi = new StripeClient(baseUrl, secretKey, Duration.ofSeconds(20))
                .createPaymentIntent(cents, "usd", paymentMethod, orderId, "contract-" + java.util.UUID.randomUUID());
        return new Intent(pi.id(), pi.status(), pi.amount(), pi.currency());
    }

    public static Refund createRefund(String baseUrl, String secretKey, String paymentIntentId, long cents, Long orderId) {
        StripeClient.Refund r = new StripeClient(baseUrl, secretKey, Duration.ofSeconds(20))
                .createRefund(paymentIntentId, cents, orderId, "contract-" + java.util.UUID.randomUUID());
        return new Refund(r.id(), r.status(), r.amount());
    }

    public record Hosted(String id, String status, long amount, String clientSecret) {
    }

    public record State(String id, String status, long amount, String orderId) {
    }

    public static Hosted createHostedIntent(String baseUrl, String secretKey, long cents, Long orderId) {
        StripeClient.HostedIntent pi = new StripeClient(baseUrl, secretKey, Duration.ofSeconds(20))
                .createHostedIntent(cents, "usd", orderId, "contract-" + java.util.UUID.randomUUID());
        return new Hosted(pi.id(), pi.status(), pi.amount(), pi.clientSecret());
    }

    public static State retrieveIntent(String baseUrl, String secretKey, String paymentIntentId) {
        StripeClient.IntentState s = new StripeClient(baseUrl, secretKey, Duration.ofSeconds(20)).retrieveIntent(paymentIntentId);
        return new State(s.id(), s.status(), s.amount(), s.orderId());
    }
}
