package com.rbctcsworld.ecommerce.payment;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Our HTTP layer against a local server that plays back recorded Stripe answers (src/test/resources/stripe):
 * the exact request we send (path, headers, form) and how every kind of answer is parsed.
 */
class StripeClientTest {

    private HttpServer server;
    private volatile Recorded last;
    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile long delayMillis;

    record Recorded(String method, String path, String auth, String contentType, String idempotencyKey,
                    Map<String, String> form) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            last = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key"), decode(raw));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private StripeClient client() {
        return new StripeClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "sk_test_123", Duration.ofSeconds(2));
    }

    private void answer(int httpStatus, String fixture) throws IOException {
        status = httpStatus;
        try (InputStream in = getClass().getResourceAsStream("/stripe/" + fixture)) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> decode(String form) {
        if (form.isEmpty()) return Map.of();
        return Arrays.stream(form.split("&")).map(p -> p.split("=", 2)).collect(Collectors.toMap(
                p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8), p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8),
                (a, b) -> b, LinkedHashMap::new));
    }

    @Test
    void paymentIntentRequestIsExactlyWhatStripeExpects() throws IOException {
        answer(200, "payment_intent_succeeded.json");

        StripeClient.PaymentIntent pi = client().createPaymentIntent(2450, "usd", "pm_card_visa", 42L, "order-42-charge-2450-pm_card_visa");

        assertThat(last.method()).isEqualTo("POST");
        assertThat(last.path()).isEqualTo("/v1/payment_intents");                      // trailing slash of the base removed
        assertThat(last.auth()).isEqualTo("Bearer sk_test_123");
        assertThat(last.contentType()).isEqualTo("application/x-www-form-urlencoded");
        assertThat(last.idempotencyKey()).isEqualTo("order-42-charge-2450-pm_card_visa");
        assertThat(last.form()).containsExactly(
                Map.entry("amount", "2450"), Map.entry("currency", "usd"), Map.entry("payment_method", "pm_card_visa"),
                Map.entry("confirm", "true"), Map.entry("automatic_payment_methods[enabled]", "true"),
                Map.entry("automatic_payment_methods[allow_redirects]", "never"), Map.entry("metadata[order_id]", "42"));
        assertThat(pi).isEqualTo(new StripeClient.PaymentIntent("pi_3QabcDEFghIJkl0A1b2C3d4E", "succeeded", 2450, "usd"));
    }

    @Test
    void refundRequestAndAnswer() throws IOException {
        answer(200, "refund_succeeded.json");

        StripeClient.Refund refund = client().createRefund("pi_3QabcDEFghIJkl0A1b2C3d4E", 1000, 42L, "order-42-refund-1-1000");

        assertThat(last.path()).isEqualTo("/v1/refunds");
        assertThat(last.idempotencyKey()).isEqualTo("order-42-refund-1-1000");
        assertThat(last.form()).containsExactly(Map.entry("payment_intent", "pi_3QabcDEFghIJkl0A1b2C3d4E"),
                Map.entry("amount", "1000"), Map.entry("metadata[order_id]", "42"));
        assertThat(refund).isEqualTo(new StripeClient.Refund("re_3QabcDEFghIJkl0A1Refund1", "succeeded", 1000));
    }

    @Test
    void requiresActionIsParsedAsAStatus() throws IOException {
        answer(200, "payment_intent_requires_action.json");
        assertThat(client().createPaymentIntent(2450, "usd", "pm_card_visa", 1L, "k").status()).isEqualTo("requires_action");
    }

    @Test
    void cardDeclineIsACardErrorWithItsCodesAndPaymentIntent() throws IOException {
        answer(402, "card_declined_insufficient_funds.json");

        assertThatThrownBy(() -> client().createPaymentIntent(2450, "usd", "pm_card_chargeDeclinedInsufficientFunds", 1L, "k"))
                .isInstanceOfSatisfying(StripeException.class, e -> {
                    assertThat(e.isCardError()).isTrue();
                    assertThat(e.status()).isEqualTo(402);
                    assertThat(e.code()).isEqualTo("card_declined");
                    assertThat(e.declineCode()).isEqualTo("insufficient_funds");
                    assertThat(e.paymentIntentId()).isEqualTo("pi_3QdeclinedNSF0A1b2C3d4E");
                    assertThat(e.getMessage()).isEqualTo("Your card has insufficient funds.");
                });
    }

    @Test
    void expiredCardHasACodeButNoDeclineCode() throws IOException {
        answer(402, "expired_card.json");
        assertThatThrownBy(() -> client().createPaymentIntent(2450, "usd", "pm_card_chargeDeclinedExpiredCard", 1L, "k"))
                .isInstanceOfSatisfying(StripeException.class, e -> {
                    assertThat(e.code()).isEqualTo("expired_card");
                    assertThat(e.declineCode()).isNull();
                });
    }

    @Test
    void wrongKeyIsNotACardError() throws IOException {
        answer(401, "invalid_api_key.json");
        assertThatThrownBy(() -> client().createRefund("pi_1", 100, 1L, "k"))
                .isInstanceOfSatisfying(StripeException.class, e -> {
                    assertThat(e.isCardError()).isFalse();
                    assertThat(e.status()).isEqualTo(401);
                    assertThat(e.type()).isEqualTo("invalid_request_error");
                    assertThat(e.paymentIntentId()).isNull();
                });
    }

    @Test
    void serverErrorWithJson() throws IOException {
        answer(500, "api_error.json");
        assertThatThrownBy(() -> client().createPaymentIntent(100, "usd", "pm_card_visa", 1L, "k"))
                .isInstanceOfSatisfying(StripeException.class, e -> assertThat(e.type()).isEqualTo("api_error"));
    }

    @Test
    void htmlErrorPageFromAProxyIsReportedNotCrashing() {
        status = 502;
        body = "<html><body>Bad gateway</body></html>";
        assertThatThrownBy(() -> client().createPaymentIntent(100, "usd", "pm_card_visa", 1L, "k"))
                .isInstanceOfSatisfying(StripeException.class, e -> {
                    assertThat(e.type()).isEqualTo("api_error");
                    assertThat(e.status()).isEqualTo(502);
                    assertThat(e.getMessage()).contains("HTTP 502");
                });
    }

    @Test
    void slowStripeTimesOutAsAConnectionError() {
        delayMillis = 3000;                                            // client timeout is 2 s
        assertThatThrownBy(() -> client().createPaymentIntent(100, "usd", "pm_card_visa", 1L, "k"))
                .isInstanceOfSatisfying(StripeException.class, e -> {
                    assertThat(e.type()).isEqualTo("api_connection_error");
                    assertThat(e.status()).isZero();
                });
    }

    @Test
    void unreachableStripeIsAConnectionError() {
        int port = server.getAddress().getPort();
        server.stop(0);
        StripeClient closed = new StripeClient("http://127.0.0.1:" + port, "sk_test_123", Duration.ofSeconds(2));
        assertThatThrownBy(() -> closed.createPaymentIntent(100, "usd", "pm_card_visa", 1L, "k"))
                .isInstanceOfSatisfying(StripeException.class, e -> assertThat(e.type()).isEqualTo("api_connection_error"));
        server = null;
    }

    @Test
    void formKeysWithBracketsAreEncoded() {
        assertThat(StripeClient.encode(new LinkedHashMap<>(Map.of("metadata[order_id]", "4 2"))))
                .isEqualTo("metadata%5Border_id%5D=4+2");
    }
}
