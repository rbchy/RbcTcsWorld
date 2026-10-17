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

    /** The token of the newest password reset e-mail to this address (waits for it). */
    public String resetToken(String address, int expectedResetMails) {
        List<Mail> resets = waitForInbox(address,
                m -> m.stream().filter(x -> x.subject().startsWith("Reset your")).count() >= expectedResetMails)
                .stream().filter(x -> x.subject().startsWith("Reset your")).toList();
        if (resets.size() < expectedResetMails) {
            throw new AssertionError("expected " + expectedResetMails + " reset e-mail(s) to " + address + ", got " + resets.size());
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)")
                .matcher(text(resets.get(0).id()));     // newest first
        if (!m.find()) throw new AssertionError("no reset link in the e-mail");
        return m.group(1);
    }
}
