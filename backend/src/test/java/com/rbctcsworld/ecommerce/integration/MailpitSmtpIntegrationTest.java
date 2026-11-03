package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.notification.MailChannel;
import com.rbctcsworld.ecommerce.notification.NotificationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real SMTP into Mailpit (Testcontainers), read back through Mailpit's API. Skipped without Docker. */
class MailpitSmtpIntegrationTest {

    static final String IMAGE = "axllent/mailpit:v1.31.5";
    private static GenericContainer<?> mailpit;
    private static String api;

    @BeforeAll
    static void start() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        mailpit = new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));
        mailpit.start();
        api = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    @AfterAll
    static void stop() {
        if (mailpit != null) mailpit.stop();
    }

    private static JsonNode get(String path) throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(api + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonMapper.builder().build().readTree(r.body());
    }

    @Test
    void anOrderEmailArrivesWithSenderSubjectAndUtf8Text() throws Exception {
        String to = "mailpit-" + UUID.randomUUID() + "@test.com";
        MailChannel smtp = NotificationTestSupport.smtp(mailpit.getHost(), mailpit.getMappedPort(1025));

        smtp.send(to, "Order ORD-1 received - $29.84", "2 x Wireless Mouse\nধন্যবাদ, Gracias, 谢谢");

        JsonNode found = get("/api/v1/search?query=" + URLEncoder.encode("to:" + to, StandardCharsets.UTF_8));
        assertThat(found.path("messages").size()).isEqualTo(1);
        JsonNode summary = found.path("messages").get(0);
        assertThat(summary.path("Subject").asString()).isEqualTo("Order ORD-1 received - $29.84");
        assertThat(summary.path("From").path("Address").asString()).isEqualTo("orders@rbctcsworld.test");
        assertThat(summary.path("To").get(0).path("Address").asString()).isEqualTo(to);

        JsonNode message = get("/api/v1/message/" + summary.path("ID").asString());
        assertThat(message.path("Text").asString()).contains("ধন্যবাদ, Gracias, 谢谢");
        assertThat(message.path("HTML").asString())                                         // ADR-0025: both parts arrive
                .contains("ধন্যবাদ, Gracias, 谢谢").contains("Order ORD-1 received - $29.84");
    }

    @Test
    void anUnreachableMailServerIsAnErrorTheOutboxRetries() {
        MailChannel nowhere = NotificationTestSupport.smtp("localhost", 1);
        assertThatThrownBy(() -> nowhere.send("a@test.com", "s", "b"))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("SMTP:");
    }
}
