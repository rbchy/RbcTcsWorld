package com.rbctcsworld.ecommerce.tax;

import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxAddress;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxLine;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Stripe Tax HTTP layer against a local server playing recorded answers: the exact request, and every answer. */
class StripeTaxClientTest {

    private static final TaxAddress PA = new TaxAddress("1 Market St", "Philadelphia", "PA", "19107");
    private HttpServer server;
    private volatile Recorded last;
    private volatile int status = 200;
    private volatile String body = "{}";

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

    private StripeTaxClient client() {
        return new StripeTaxClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "sk_test_123", Duration.ofSeconds(2));
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
                p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8), (a, b) -> b, LinkedHashMap::new));
    }

    @Test
    void aCalculationSendsEveryLineTheShippingAndTheDeliveryAddress() throws IOException {
        answer(200, "tax_calculation_created.json");
        StripeTaxClient.Calculation c = client().calculate("usd",
                List.of(new TaxLine("SKU-1", new BigDecimal("60.00"), 2), new TaxLine("SKU-2", new BigDecimal("40.00"), 1)),
                599, PA, "taxcalc-key-1");

        assertThat(c).isEqualTo(new StripeTaxClient.Calculation("taxcalc_1QexampleTaxCalc", 800, 11199));
        assertThat(last.method()).isEqualTo("POST");
        assertThat(last.path()).isEqualTo("/v1/tax/calculations");
        assertThat(last.auth()).isEqualTo("Bearer sk_test_123");
        assertThat(last.contentType()).isEqualTo("application/x-www-form-urlencoded");
        assertThat(last.idempotencyKey()).isEqualTo("taxcalc-key-1");
        assertThat(last.form()).containsExactly(
                Map.entry("currency", "usd"),
                Map.entry("line_items[0][amount]", "6000"), Map.entry("line_items[0][reference]", "SKU-1"),
                Map.entry("line_items[0][quantity]", "2"), Map.entry("line_items[0][tax_behavior]", "exclusive"),
                Map.entry("line_items[1][amount]", "4000"), Map.entry("line_items[1][reference]", "SKU-2"),
                Map.entry("line_items[1][quantity]", "1"), Map.entry("line_items[1][tax_behavior]", "exclusive"),
                Map.entry("shipping_cost[amount]", "599"),
                Map.entry("customer_details[address][line1]", "1 Market St"),
                Map.entry("customer_details[address][city]", "Philadelphia"),
                Map.entry("customer_details[address][state]", "PA"),
                Map.entry("customer_details[address][postal_code]", "19107"),
                Map.entry("customer_details[address][country]", "US"),
                Map.entry("customer_details[address_source]", "shipping"));
    }

    @Test
    void aLineWithAProductTaxCodeSendsItAndALineWithoutLeavesItToTheAccountDefault() throws IOException {
        answer(200, "tax_calculation_created.json");
        client().calculate("usd", List.of(new TaxLine("TEE", new BigDecimal("20.00"), 1, "txcd_30011000"),
                new TaxLine("MUG", new BigDecimal("12.00"), 1)), 599, PA, "k");
        assertThat(last.form()).containsEntry("line_items[0][tax_code]", "txcd_30011000")
                .doesNotContainKey("line_items[1][tax_code]");
    }

    @Test
    void aPaidOrderIsRecordedFromItsCalculation() throws IOException {
        answer(200, "tax_transaction_created.json");
        StripeTaxClient.Transaction t = client().recordSale("taxcalc_1", "RBC-20261027-ABC", "tax-RBC-20261027-ABC");
        assertThat(t).isEqualTo(new StripeTaxClient.Transaction("tax_1QexampleTransaction", "RBC-20261027-ABC"));
        assertThat(last.path()).isEqualTo("/v1/tax/transactions/create_from_calculation");
        assertThat(last.idempotencyKey()).isEqualTo("tax-RBC-20261027-ABC");
        assertThat(last.form()).containsExactly(Map.entry("calculation", "taxcalc_1"), Map.entry("reference", "RBC-20261027-ABC"));
    }

    @Test
    void anErrorAnswerCarriesStripesStatusCodeAndMessage() throws IOException {
        answer(400, "tax_location_invalid.json");
        assertThatThrownBy(() -> client().calculate("usd", List.of(new TaxLine("A", BigDecimal.TEN, 1)), 0, PA, "k"))
                .isInstanceOfSatisfying(StripeTaxException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.code()).isEqualTo("customer_tax_location_invalid");
                    assertThat(e.getMessage()).startsWith("We could not determine");
                });
    }

    @Test
    void anUnreadableAnswerIsAnApiError() {
        status = 502;
        body = "<html>Bad gateway</html>";
        assertThatThrownBy(() -> client().recordSale("taxcalc_1", "R", "k"))
                .isInstanceOfSatisfying(StripeTaxException.class, e -> {
                    assertThat(e.status()).isEqualTo(502);
                    assertThat(e.code()).isEqualTo("api_error");
                    assertThat(e.getMessage()).contains("HTTP 502");
                });
    }

    @Test
    void anErrorWithoutDetailsStillFails() {
        status = 500;
        body = "{\"error\":{\"code\":42}}";
        assertThatThrownBy(() -> client().recordSale("taxcalc_1", "R", "k"))
                .isInstanceOfSatisfying(StripeTaxException.class, e -> {
                    assertThat(e.status()).isEqualTo(500);
                    assertThat(e.code()).isNull();                       // not a string: not read as one
                });
    }

    @Test
    void stripeNotReachableIsStatusZero() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        StripeTaxClient offline = new StripeTaxClient("http://127.0.0.1:" + closedPort, "sk_test_123", Duration.ofSeconds(2));
        assertThatThrownBy(() -> offline.recordSale("taxcalc_1", "R", "k"))
                .isInstanceOfSatisfying(StripeTaxException.class, e -> {
                    assertThat(e.status()).isZero();
                    assertThat(e.code()).isEqualTo("api_connection_error");
                });
    }

    @Test
    void amountsAreSentInCentsRoundedHalfUp() {
        assertThat(StripeTaxClient.cents(new BigDecimal("12.34"))).isEqualTo(1234);
        assertThat(StripeTaxClient.cents(new BigDecimal("0.005"))).isEqualTo(1);
        assertThat(StripeTaxClient.cents(new BigDecimal("5"))).isEqualTo(500);
    }

    @Test
    void formValuesAreUrlEncoded() {
        assertThat(StripeTaxClient.encode(Map.of("customer_details[address][line1]", "1 Main St & 2")))
                .isEqualTo("customer_details%5Baddress%5D%5Bline1%5D=1+Main+St+%26+2");
    }
}
