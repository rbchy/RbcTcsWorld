package com.rbctcsworld.ecommerce.fulfillment;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Shippo HTTP layer against a local server playing recorded answers: the exact request, and every answer. */
class ShippoClientTest {

    private static final ShippingProvider.Address FROM =
            new ShippingProvider.Address("RbcTcsWorld Shop", "965 Mission St", null, "San Francisco", "CA", "94103", "4155550100");
    private static final ShippingProvider.Address TO =
            new ShippingProvider.Address("Ada Lovelace", "12 Main St", "Apt 4", "Pittsburgh", "PA", "15213", null);

    private HttpServer server;
    private volatile Recorded last;
    private volatile int status = 200;
    private volatile String body = "{}";

    record Recorded(String method, String path, String auth, String contentType, String body) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            last = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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

    private ShippoClient client() {
        return new ShippoClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "shippo_test_123", Duration.ofSeconds(2));
    }

    private void answer(int httpStatus, String fixture) throws IOException {
        status = httpStatus;
        try (InputStream in = getClass().getResourceAsStream("/shippo/" + fixture)) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JsonNode sent() {
        return JsonMapper.builder().build().readTree(last.body());
    }

    @Test
    void ratesAreAskedForOneParcelFromTheStoreToTheCustomer() throws IOException {
        answer(201, "shipment_rates.json");
        List<ShippoClient.Rate> rates = client().rates(FROM, TO, new ShippoClient.Parcel("10", "8", "4", "48"), "Order ORD-1");

        assertThat(last.method()).isEqualTo("POST");
        assertThat(last.path()).isEqualTo("/shipments/");
        assertThat(last.auth()).isEqualTo("ShippoToken shippo_test_123");
        assertThat(last.contentType()).isEqualTo("application/json");
        JsonNode s = sent();
        assertThat(s.path("address_from").toString()).isEqualTo(
                "{\"name\":\"RbcTcsWorld Shop\",\"street1\":\"965 Mission St\",\"city\":\"San Francisco\",\"state\":\"CA\","
                        + "\"zip\":\"94103\",\"country\":\"US\",\"phone\":\"4155550100\"}");
        assertThat(s.path("address_to").toString()).isEqualTo(
                "{\"name\":\"Ada Lovelace\",\"street1\":\"12 Main St\",\"street2\":\"Apt 4\",\"city\":\"Pittsburgh\","
                        + "\"state\":\"PA\",\"zip\":\"15213\",\"country\":\"US\"}");
        assertThat(s.path("parcels").toString()).isEqualTo(
                "[{\"length\":\"10\",\"width\":\"8\",\"height\":\"4\",\"distance_unit\":\"in\",\"weight\":\"48\",\"mass_unit\":\"oz\"}]");
        assertThat(s.path("async").toString()).isEqualTo("false");
        assertThat(s.path("metadata").asString()).isEqualTo("Order ORD-1");

        assertThat(rates).containsExactly(
                new ShippoClient.Rate("rate_usps_priority", "USPS", "Priority Mail", new BigDecimal("9.14"), "USD"),
                new ShippoClient.Rate("rate_usps_ground", "USPS", "Ground Advantage", new BigDecimal("7.58"), "USD"),
                new ShippoClient.Rate("rate_ups_ground", "UPS", "Ground", new BigDecimal("12.40"), "USD"),
                new ShippoClient.Rate("rate_fedex_ground", "FedEx", "Ground", new BigDecimal("11.95"), "USD"));   // no price: left out
    }

    @Test
    void noRatesIsAnEmptyList() {
        body = "{\"object_id\":\"s1\",\"rates\":[]}";
        assertThat(client().rates(FROM, TO, new ShippoClient.Parcel("1", "1", "1", "1"), "m")).isEmpty();
        body = "{\"object_id\":\"s1\"}";
        assertThat(client().rates(FROM, TO, new ShippoClient.Parcel("1", "1", "1", "1"), "m")).isEmpty();
    }

    @Test
    void buyingALabelSendsTheRateAndReadsTheTrackingNumberAndPdf() throws IOException {
        answer(201, "transaction_success.json");
        ShippoClient.Transaction t = client().buy("rate_usps_ground", "Order ORD-1");

        assertThat(last.path()).isEqualTo("/transactions/");
        assertThat(sent().toString()).isEqualTo(
                "{\"rate\":\"rate_usps_ground\",\"label_file_type\":\"PDF\",\"async\":false,\"metadata\":\"Order ORD-1\"}");
        assertThat(t).isEqualTo(new ShippoClient.Transaction("70ae8117ee1749e393f249d5b77c45e0", "SUCCESS",
                "9400111899223197428490", "https://deliver.goshippo.com/70ae8117ee1749e393f249d5b77c45e0.pdf", ""));
    }

    @Test
    void aFailedLabelCarriesTheCarriersMessages() throws IOException {
        answer(201, "transaction_error.json");
        ShippoClient.Transaction t = client().buy("rate_usps_ground", "m");
        assertThat(t.status()).isEqualTo("ERROR");
        assertThat(t.messages()).isEqualTo("The address as submitted could not be found.; Please check for missing or incorrect information.");
    }

    @Test
    void theTrackingStatusIsReadFromShippo() throws IOException {
        answer(200, "track_delivered.json");
        assertThat(client().trackingStatus("usps", "9400 1118")).isEqualTo("DELIVERED");
        assertThat(last.method()).isEqualTo("GET");
        assertThat(last.path()).isEqualTo("/tracks/usps/9400+1118");                   // path parts are encoded
        assertThat(last.auth()).isEqualTo("ShippoToken shippo_test_123");
        body = "{\"tracking_status\":null}";
        assertThat(client().trackingStatus("usps", "1")).isNull();
    }

    @Test
    void anErrorAnswerCarriesShipposDetailOrTheShortBody() throws IOException {
        answer(401, "error_detail.json");
        assertThatThrownBy(() -> client().trackingStatus("usps", "1")).isInstanceOfSatisfying(ShippoException.class, e -> {
            assertThat(e.status()).isEqualTo(401);
            assertThat(e.getMessage()).isEqualTo("Invalid token.");
        });
        answer(400, "error_fields.json");
        assertThatThrownBy(() -> client().buy("r", "m")).isInstanceOfSatisfying(ShippoException.class, e -> {
            assertThat(e.status()).isEqualTo(400);
            assertThat(e.getMessage()).isEqualTo("{\"address_to\":{\"zip\":[\"This field may not be blank.\"]}}");
        });
        status = 500;
        body = "{\"error\":\"" + "x".repeat(400) + "\"}";
        assertThatThrownBy(() -> client().buy("r", "m")).isInstanceOfSatisfying(ShippoException.class,
                e -> assertThat(e.getMessage()).hasSize(303).endsWith("..."));
    }

    @Test
    void anUnreadableAnswerSaysItsStatus() {
        status = 502;
        body = "<html>Bad gateway</html>";
        assertThatThrownBy(() -> client().buy("r", "m")).isInstanceOfSatisfying(ShippoException.class, e -> {
            assertThat(e.status()).isEqualTo(502);
            assertThat(e.getMessage()).isEqualTo("Unreadable answer from Shippo (HTTP 502)");
        });
    }

    @Test
    void shippoNotReachableIsStatusZero() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        ShippoClient offline = new ShippoClient("http://127.0.0.1:" + closedPort, "shippo_test_123", Duration.ofSeconds(2));
        assertThatThrownBy(() -> offline.trackingStatus("usps", "1")).isInstanceOfSatisfying(ShippoException.class,
                e -> assertThat(e.status()).isZero());
    }
}
