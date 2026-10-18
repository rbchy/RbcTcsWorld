package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.MailpitClient;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Two-factor sign-in by e-mail code, end to end through Mailpit (REQ-AUTH-16). Skipped without Mailpit. */
@Tag("api")
@Tag("security")
@Epic("Identity and access")
@Feature("Two-factor sign-in")
class TwoFactorApiTest {

    private final AuthClient auth = new AuthClient();
    private static final MailpitClient MAILPIT = new MailpitClient();

    @BeforeAll
    static void mailServer() {
        assumeTrue(MAILPIT.isAvailable(), "Mailpit is not running (-DmailpitUrl)");
    }

    private String[] customerWithTwoFactor() {
        String email = TestData.uniqueEmail();
        String token = auth.registerAndGetToken(email, TestData.PASSWORD);
        auth.setTwoFactor(token, true, TestData.PASSWORD).then().statusCode(200).body("enabled", equalTo(true));
        return new String[] {email, token};
    }

    @Test
    @Tag("smoke")
    @DisplayName("Password alone gives no tokens; the e-mailed code finishes the sign-in; the code works once")
    void codeFinishesSignIn() {
        String email = customerWithTwoFactor()[0];

        String challenge = auth.login(email, TestData.PASSWORD).then().statusCode(200)
                .body("twoFactorRequired", equalTo(true)).body("$", not(hasKey("token"))).body("$", not(hasKey("refreshToken")))
                .extract().path("challengeToken");
        String code = MAILPIT.loginCode(email, 1);

        auth.verifyLogin(challenge, code).then().statusCode(200).body("email", equalTo(email)).body("token", org.hamcrest.Matchers.notNullValue());
        auth.verifyLogin(challenge, code).then().statusCode(400);     // used up
    }

    @Test
    @DisplayName("A wrong code is 400; after 5 wrong guesses even the right code is refused")
    void wrongCodes() {
        String email = customerWithTwoFactor()[0];
        String challenge = auth.login(email, TestData.PASSWORD).then().statusCode(200).extract().path("challengeToken");
        String code = MAILPIT.loginCode(email, 1);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            auth.verifyLogin(challenge, wrong).then().statusCode(400).body("message", containsString("wrong or has expired"));
        }
        auth.verifyLogin(challenge, code).then().statusCode(400);
    }

    @Test
    @DisplayName("A new sign-in ends the older code; a code that is not 6 digits is 400 before it is even checked")
    void newerChallengeWins() {
        String email = customerWithTwoFactor()[0];
        String first = auth.login(email, TestData.PASSWORD).then().statusCode(200).extract().path("challengeToken");
        String firstCode = MAILPIT.loginCode(email, 1);
        auth.login(email, TestData.PASSWORD).then().statusCode(200);
        auth.verifyLogin(first, firstCode).then().statusCode(400);
        auth.verifyLogin(first, "12ab56").then().statusCode(400);
    }

    @Test
    @DisplayName("Turning it off needs the password; afterwards login gives tokens again")
    void turnOff() {
        String[] c = customerWithTwoFactor();
        auth.twoFactorState(c[1]).then().statusCode(200).body("enabled", equalTo(true));
        auth.setTwoFactor(c[1], false, "wrong-Password1").then().statusCode(401);
        auth.setTwoFactor(c[1], false, TestData.PASSWORD).then().statusCode(200).body("enabled", equalTo(false));
        auth.login(c[0], TestData.PASSWORD).then().statusCode(200).body("token", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @DisplayName("A guest cannot turn it on (no password yet) and the page needs a sign-in")
    void guestAndAnonymous() {
        String guest = auth.guest(TestData.uniqueEmail()).then().statusCode(201).extract().path("token");
        auth.setTwoFactor(guest, true, "anything-1234").then().statusCode(400).body("message", containsString("Set a password first"));
        auth.twoFactorState(null).then().statusCode(401);
    }
}
