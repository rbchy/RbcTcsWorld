package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.MailpitClient;
import com.rbctcsworld.ecommerce.qa.api.MailpitClient.Mail;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * "Forgot password" end to end: API -> outbox -> SMTP -> Mailpit -> link -> new password (REQ-AUTH-12..14).
 * Skipped when no Mailpit is running (-DmailpitUrl).
 */
@Tag("api")
@Tag("security")
@Epic("Identity and access")
@Feature("Password reset")
class PasswordResetApiTest {

    private static final String ACCEPTED = "If an account exists for this e-mail, we have sent a link to reset the password.";
    private static final String INVALID = "This reset link is invalid or has expired. Please ask for a new one.";

    private final AuthClient auth = new AuthClient();
    private static final MailpitClient MAILPIT = new MailpitClient();

    @BeforeAll
    static void mailServer() {
        assumeTrue(MAILPIT.isAvailable(), "Mailpit is not running (-DmailpitUrl)");
    }

    private String customer() {
        String email = TestData.uniqueEmail();
        auth.register(email, TestData.PASSWORD).then().statusCode(201);
        return email;
    }

    @Test
    @Tag("smoke")
    @DisplayName("Reset by e-mail: new password works, old one and old sessions do not, link works once, 'changed' e-mail")
    void resetByEmail() {
        String email = customer();
        String oldSession = auth.login(email, TestData.PASSWORD).then().statusCode(200).extract().path("refreshToken");

        auth.forgotPassword(email).then().statusCode(202).body("message", equalTo(ACCEPTED));
        String token = MAILPIT.resetToken(email, 1);
        auth.resetPassword(token, "BrandNew1!").then().statusCode(204);

        auth.login(email, TestData.PASSWORD).then().statusCode(401);
        auth.login(email, "BrandNew1!").then().statusCode(200);
        auth.refresh(oldSession).then().statusCode(401);
        auth.resetPassword(token, "Another1!").then().statusCode(400).body("message", equalTo(INVALID));

        List<Mail> inbox = MAILPIT.waitForInbox(email, m -> m.size() >= 2);
        assertEquals("Your RbcTcsWorld password was changed", inbox.get(0).subject());
        assertTrue(MAILPIT.text(inbox.get(0).id()).contains("every device was signed out"));
    }

    @Test
    @DisplayName("Account enumeration: an unknown e-mail gets the same 202 and the same words, and no e-mail is sent")
    void sameAnswerForEveryone() {
        String stranger = TestData.uniqueEmail();
        String known = auth.forgotPassword(customer()).then().statusCode(202).extract().asString();
        String unknown = auth.forgotPassword(stranger).then().statusCode(202).extract().asString();

        assertEquals(known, unknown);
        assertEquals(0, MAILPIT.inbox(stranger).size());
    }

    @Test
    @DisplayName("A newer link replaces the older one; a made-up link is refused")
    void onlyTheNewestLinkWorks() {
        String email = customer();
        auth.forgotPassword(email).then().statusCode(202);
        String first = MAILPIT.resetToken(email, 1);
        auth.forgotPassword(email).then().statusCode(202);
        String second = MAILPIT.resetToken(email, 2);

        auth.resetPassword(first, "BrandNew1!").then().statusCode(400).body("message", equalTo(INVALID));
        auth.resetPassword("x".repeat(43), "BrandNew1!").then().statusCode(400).body("message", equalTo(INVALID));
        auth.resetPassword(second, "BrandNew1!").then().statusCode(204);
    }

    @Test
    @DisplayName("Validation: weak new password and missing fields -> 400 with field names")
    void validation() {
        auth.resetPassword("any-token", "short").then().statusCode(400).body("fieldErrors", hasKey("newPassword"));
        auth.resetPassword("", "BrandNew1!").then().statusCode(400).body("fieldErrors", hasKey("token"));
        auth.forgotPassword("not-an-email").then().statusCode(400).body("fieldErrors", hasKey("email"));
    }

    @Test
    @DisplayName("At most 3 reset e-mails per account and hour, the answer never changes")
    void hourlyLimit() {
        String email = customer();
        for (int i = 0; i < 5; i++) {
            auth.forgotPassword(email).then().statusCode(202).body("message", equalTo(ACCEPTED));
        }
        MAILPIT.resetToken(email, 3);
        try {
            Thread.sleep(3000);                                   // a 4th e-mail would have arrived by now
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(3, MAILPIT.inbox(email).size());
    }
}
