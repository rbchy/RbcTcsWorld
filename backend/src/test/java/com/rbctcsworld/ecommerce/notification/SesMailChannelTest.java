package com.rbctcsworld.ecommerce.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The SES channel against a local server playing SES: the signed request it sends and every kind of answer. */
class SesMailChannelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC);
    private static final String FROM = "RbcTcsWorld Shop <orders@shop.example.com>";

    private HttpServer server;
    private volatile String path;
    private volatile String body;
    private volatile String authorization;
    private volatile String amzDate;
    private volatile String contentType;
    private volatile int status = 200;
    private volatile String answer = "{\"MessageId\":\"0100019a-ok\"}";
    private volatile String errorType;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path = exchange.getRequestURI().getPath();
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            amzDate = exchange.getRequestHeaders().getFirst("X-Amz-Date");
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (errorType != null) exchange.getResponseHeaders().add("x-amzn-ErrorType", errorType);
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
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

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private SesMailChannel ses(String configurationSet) {
        return new SesMailChannel(base() + "/", "us-east-1", "AKIDEXAMPLE", "secret", null, FROM, configurationSet,
                Duration.ofSeconds(2), CLOCK);
    }

    @Test
    void theEmailIsSentFromTheStoresDomainSignedForSes() {
        ses("rbctcsworld-events").send("ada@example.com", "Your order ORD-1", "Thank you for your order.");

        assertThat(path).isEqualTo("/v2/email/outbound-emails");
        assertThat(contentType).isEqualTo("application/json");
        JsonNode sent = JsonMapper.builder().build().readTree(body);
        assertThat(sent.path("FromEmailAddress").asString()).isEqualTo(FROM);
        assertThat(sent.path("Destination").path("ToAddresses").toString()).isEqualTo("[\"ada@example.com\"]");
        JsonNode simple = sent.path("Content").path("Simple");
        assertThat(simple.path("Subject").path("Data").asString()).isEqualTo("Your order ORD-1");
        assertThat(simple.path("Subject").path("Charset").asString()).isEqualTo("UTF-8");
        assertThat(simple.path("Body").path("Text").path("Data").asString()).isEqualTo("Thank you for your order.");
        assertThat(simple.path("Body").path("Text").path("Charset").asString()).isEqualTo("UTF-8");
        assertThat(simple.path("Body").path("Html").path("Data").asString())                 // ADR-0025: and its HTML twin
                .startsWith("<!doctype html>").contains("Thank you for your order.", ">RbcTcsWorld Shop<");
        assertThat(simple.path("Body").path("Html").path("Charset").asString()).isEqualTo("UTF-8");
        assertThat(sent.path("ConfigurationSetName").asString()).isEqualTo("rbctcsworld-events");

        // the signature covers exactly the body and host that went out
        assertThat(amzDate).isEqualTo("20261028T030000Z");
        assertThat(authorization).isEqualTo(AwsSigV4.sign(URI.create(base() + "/v2/email/outbound-emails"), "application/json",
                body, "us-east-1", "ses", "AKIDEXAMPLE", "secret", null, CLOCK.instant()).get("Authorization"));
        assertThat(authorization).startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261028/us-east-1/ses/aws4_request");
    }

    @Test
    void withoutAConfigurationSetNoneIsNamed() {
        ses(null).send("ada@example.com", "s", "b");
        assertThat(JsonMapper.builder().build().readTree(body).has("ConfigurationSetName")).isFalse();
        ses(" ").send("ada@example.com", "s", "b");
        assertThat(JsonMapper.builder().build().readTree(body).has("ConfigurationSetName")).isFalse();
    }

    @Test
    void anErrorAnswerFailsTheAttemptWithSesTypeAndMessage() {
        status = 400;
        errorType = "MessageRejected:http://internal.amazon.com/coral/com.amazonaws.sesv2/";
        answer = "{\"message\":\"Email address is not verified.\"}";
        assertThatThrownBy(() -> ses(null).send("ada@example.com", "s", "b")).isInstanceOf(IllegalStateException.class)
                .hasMessage("SES 400 MessageRejected: Email address is not verified.");

        status = 429;
        errorType = null;
        answer = "{\"Message\":\"Maximum sending rate exceeded.\"}";
        assertThatThrownBy(() -> ses(null).send("ada@example.com", "s", "b"))
                .hasMessage("SES 429: Maximum sending rate exceeded.");
    }

    @Test
    void anErrorThatIsNotJsonIsShownShort() {
        status = 503;
        answer = "<html>" + "x".repeat(300) + "</html>";
        assertThatThrownBy(() -> ses(null).send("ada@example.com", "s", "b"))
                .hasMessageStartingWith("SES 503: <html>xxx").hasMessageEndingWith("...");
        assertThat(SesMailChannel.message("{\"code\":1}")).isEqualTo("{\"code\":1}");
        assertThat(SesMailChannel.message("")).isEmpty();
    }

    @Test
    void sesNotReachableFailsTheAttempt() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        SesMailChannel offline = new SesMailChannel("http://127.0.0.1:" + closedPort, "us-east-1", "AKID", "secret", "token",
                FROM, null, Duration.ofSeconds(2), CLOCK);
        assertThatThrownBy(() -> offline.send("ada@example.com", "s", "b")).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("SES not reachable");
        assertThat(offline.name()).isEqualTo("ses");
    }

    @Test
    void temporaryKeysSendTheSessionToken() {
        new SesMailChannel(base(), "us-east-1", "AKID", "secret", "TOKEN", FROM, null, Duration.ofSeconds(2), CLOCK)
                .send("ada@example.com", "s", "b");
        assertThat(authorization).contains("SignedHeaders=content-type;host;x-amz-date;x-amz-security-token");
    }

    @Test
    void theStartRefusesSesWithoutRegionKeysOrAValidSender() {
        NotificationConfig.Ses ok = new NotificationConfig.Ses("us-east-1", "AKID", "secret", null, null, null);
        assertThat(NotificationConfig.create(" SES ", "h", 1, FROM, ok, CLOCK)).isInstanceOf(SesMailChannel.class);
        assertThat(NotificationConfig.create("ses", "h", 1, FROM,
                new NotificationConfig.Ses("eu-west-2", " AKID ", " s ", " T ", " set ", " " + base() + " "), CLOCK)).isInstanceOf(SesMailChannel.class);
        for (NotificationConfig.Ses bad : new NotificationConfig.Ses[] {
                null,
                new NotificationConfig.Ses("", "AKID", "secret", null, null, null),
                new NotificationConfig.Ses("us-east", "AKID", "secret", null, null, null),
                new NotificationConfig.Ses("US-EAST-1", "AKID", "secret", null, null, null)}) {
            assertThatThrownBy(() -> NotificationConfig.create("ses", "h", 1, FROM, bad, CLOCK))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("SES_REGION");
        }
        assertThatThrownBy(() -> NotificationConfig.create("ses", "h", 1, FROM,
                new NotificationConfig.Ses("us-east-1", " ", "secret", null, null, null), CLOCK)).hasMessageContaining("AWS_ACCESS_KEY_ID");
        assertThatThrownBy(() -> NotificationConfig.create("ses", "h", 1, FROM,
                new NotificationConfig.Ses("us-east-1", "AKID", null, null, null, null), CLOCK)).hasMessageContaining("AWS_SECRET_ACCESS_KEY");
        assertThatThrownBy(() -> NotificationConfig.create("ses", "h", 1, "orders at shop", ok, CLOCK)).hasMessageContaining("MAIL_FROM");
        assertThatCode(() -> NotificationConfig.create("ses", "h", 1, "orders@shop.example.com", ok, CLOCK)).doesNotThrowAnyException();
    }
}
