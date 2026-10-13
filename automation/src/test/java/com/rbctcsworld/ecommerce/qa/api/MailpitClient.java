package com.rbctcsworld.ecommerce.qa.api;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import io.restassured.path.json.JsonPath;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.restassured.RestAssured.given;

/**
 * Reads the inbox of Mailpit, the test mail server (https://mailpit.axllent.org). The shop sends e-mails in
 * the background, so the checks wait until the expected mail arrives instead of sleeping a fixed time.
 */
public class MailpitClient {

    public record Mail(String id, String to, String subject) {
    }

    private final String base = TestConfig.mailpitUrl();

    public boolean isAvailable() {
        try {
            return given().baseUri(base).get("/api/v1/messages?limit=1").statusCode() == 200;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** All e-mails to this address, newest first. */
    public List<Mail> inbox(String address) {
        JsonPath json = given().baseUri(base).queryParam("query", "to:\"" + address + "\"")
                .get("/api/v1/search").then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> messages = json.getList("messages");
        return messages.stream().map(m -> new Mail((String) m.get("ID"), address, (String) m.get("Subject"))).toList();
    }

    /** Waits (up to 20 s) until the inbox matches, then returns it. */
    public List<Mail> waitForInbox(String address, Predicate<List<Mail>> ready) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        List<Mail> inbox = inbox(address);
        while (!ready.test(inbox) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            inbox = inbox(address);
        }
        return inbox;
    }

    /** Plain-text body of one e-mail. */
    public String text(String id) {
        return given().baseUri(base).get("/api/v1/message/{id}", id).then().statusCode(200).extract().path("Text");
    }
}
