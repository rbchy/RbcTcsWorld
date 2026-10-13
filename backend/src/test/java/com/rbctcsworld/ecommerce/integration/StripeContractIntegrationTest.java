package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.payment.PaymentGateway;
import com.rbctcsworld.ecommerce.payment.StripeTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Consumer contract with Stripe: every request the shop sends is checked by stripe-mock, Stripe's own
 * server built from its OpenAPI specification (URL, parameter names, types). It cannot test behaviour
 * (declines) - StripeClientTest replays recorded answers for that, StripeSandboxIntegrationTest uses test mode.
 * Skipped when Docker is not available.
 */
class StripeContractIntegrationTest {

    /** Pinned: a new Stripe API version must be a deliberate update, not a surprise in CI. */
    static final String IMAGE = "stripe/stripe-mock:v0.206.0";
    private static final String KEY = "sk_test_contract";
    private static GenericContainer<?> stripe;
    private static String url;

    @BeforeAll
    static void startStripeMock() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        stripe = new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(12111)
                .waitingFor(Wait.forListeningPort());
        stripe.start();
        url = "http://" + stripe.getHost() + ":" + stripe.getMappedPort(12111);
    }

    @AfterAll
    static void stop() {
        if (stripe != null) stripe.stop();
    }

    private static HttpResponse<String> raw(String body, boolean withKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + "/v1/payment_intents"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (withKey) b.header("Authorization", "Bearer " + KEY);
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void ourPaymentIntentRequestIsAValidStripeRequest() {
        StripeTestSupport.Intent pi = StripeTestSupport.createIntent(url, KEY, 2450, "pm_card_visa", 42L);
        assertThat(pi.id()).startsWith("pi_");
        assertThat(pi.amount()).isEqualTo(2450);                       // stripe-mock echoes valid parameters
        assertThat(pi.currency()).isEqualTo("usd");
        assertThat(pi.status()).isNotBlank();
    }

    @Test
    void ourRefundRequestIsAValidStripeRequest() {
        StripeTestSupport.Refund refund = StripeTestSupport.createRefund(url, KEY, "pi_123", 1000, 42L);
        assertThat(refund.id()).startsWith("re_");
        assertThat(refund.amount()).isEqualTo(1000);
    }

    @Test
    void theWholeGatewayTalksToStripe() {
        PaymentGateway gateway = StripeTestSupport.gateway(url, KEY);
        PaymentGateway.GatewayResult charge = gateway.charge(42L, "4242424242424242", new BigDecimal("24.50"));
        assertThat(charge.reference()).startsWith("pi_");             // request accepted; status is canned by stripe-mock
        assertThat(gateway.refund(42L, charge.reference(), new BigDecimal("10.00"), 1).reference()).startsWith("re_");
    }

    // ---------- the mock really checks: otherwise the tests above would prove nothing ----------

    @Test
    void stripeRejectsDollarAmounts() throws Exception {
        HttpResponse<String> r = raw("amount=24.50&currency=usd", true);
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("invalid_request_error").contains("amount");
    }

    @Test
    void stripeRejectsUnknownParameters() throws Exception {
        HttpResponse<String> r = raw("amount=2450&currency=usd&card_number=4242424242424242", true);
        assertThat(r.statusCode()).isEqualTo(400);
    }

    @Test
    void stripeRejectsRequestsWithoutAKey() throws Exception {
        assertThat(raw("amount=2450&currency=usd", false).statusCode()).isEqualTo(401);
    }
}
