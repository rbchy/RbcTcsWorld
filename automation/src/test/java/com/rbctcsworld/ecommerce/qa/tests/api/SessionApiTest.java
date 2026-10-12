package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Refresh tokens: short access token, rotation on every refresh, reuse detection, server-side logout. */
@Tag("api")
@Tag("security")
@Epic("Identity & Access")
@Feature("Sessions and refresh tokens")
class SessionApiTest {

    private final AuthClient auth = new AuthClient();
    private final CartClient cart = new CartClient();

    private Response newSession() {
        Response r = auth.register(TestData.uniqueEmail(), TestData.PASSWORD);
        r.then().statusCode(201);
        return r;
    }

    @Test
    @DisplayName("Login returns a 15-minute access token and a refresh token")
    void loginReturnsBothTokens() {
        String email = TestData.uniqueEmail();
        auth.register(email, TestData.PASSWORD).then().statusCode(201);
        auth.login(email, TestData.PASSWORD).then().statusCode(200)
                .body("expiresIn", equalTo(900))
                .body("refreshToken.length()", equalTo(43));
    }

    @Test
    @DisplayName("Refresh: new access token works, refresh token is rotated (never the same twice)")
    void refreshRotates() {
        Response first = newSession();
        Response second = auth.refresh(first.path("refreshToken"));
        second.then().statusCode(200).body("email", equalTo(first.path("email")));

        assertAll(
                () -> assertNotEquals((String) first.path("refreshToken"), (String) second.path("refreshToken")),
                () -> assertEquals(200, cart.get(second.path("token")).statusCode(), "new access token is accepted"));
    }

    @Test
    @DisplayName("A spent refresh token used again after its successor ends the whole session (theft detection)")
    void reuseRevokesTheFamily() {
        String stolen = newSession().path("refreshToken");
        String next = auth.refresh(stolen).then().statusCode(200).extract().path("refreshToken");
        String current = auth.refresh(next).then().statusCode(200).extract().path("refreshToken");

        auth.refresh(stolen).then().statusCode(401)
                .body("message", equalTo("Session expired. Please log in again."));
        auth.refresh(current).then().statusCode(401);                // the real user must log in again too
    }

    @Test
    @DisplayName("Lost response (page reloaded during refresh): the spent token works once more within 30 s")
    void lostResponseIsNotTheft() {
        String first = newSession().path("refreshToken");
        auth.refresh(first).then().statusCode(200);                  // answer never reached the browser
        String retry = auth.refresh(first).then().statusCode(200).extract().path("refreshToken");
        auth.refresh(retry).then().statusCode(200);
    }

    @Test
    @DisplayName("Logout revokes the refresh token on the server; logging out twice is fine")
    void logoutEndsTheSession() {
        String token = newSession().path("refreshToken");
        auth.logout(token).then().statusCode(204);
        auth.refresh(token).then().statusCode(401);
        auth.logout(token).then().statusCode(204);
    }

    @Test
    @DisplayName("Unknown, empty or oversized refresh tokens are rejected")
    void badTokens() {
        auth.refresh("made-up-token").then().statusCode(401);
        auth.refresh("").then().statusCode(400);
        auth.refresh("x".repeat(201)).then().statusCode(400);
    }

    @Test
    @DisplayName("A refresh token is not an access token")
    void refreshTokenCannotCallTheApi() {
        String refresh = newSession().path("refreshToken");
        assertEquals(401, cart.get(refresh).statusCode());
    }
}
