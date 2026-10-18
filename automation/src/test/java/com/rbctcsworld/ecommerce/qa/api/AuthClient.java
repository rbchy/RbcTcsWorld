package com.rbctcsworld.ecommerce.qa.api;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import io.restassured.response.Response;

import java.util.Map;

public class AuthClient extends ApiClient {

    public AuthClient() {
    }

    /** Talks to another backend instance (see TestConfig.secondBaseUrl). */
    public AuthClient(String baseUrl) {
        super(baseUrl);
    }

    public Response register(String email, String password) {
        return anonymous().body(Map.of("email", email, "password", password)).post("/api/auth/register");
    }

    public Response login(String email, String password) {
        return anonymous().body(Map.of("email", email, "password", password)).post("/api/auth/login");
    }

    /** Guest checkout: a customer session without a password of its own. */
    public Response guest(String email) {
        return anonymous().body(Map.of("email", email)).post("/api/auth/guest");
    }

    /** Second step of a two-factor sign-in. */
    public Response verifyLogin(String challengeToken, String code) {
        return anonymous().body(Map.of("challengeToken", challengeToken, "code", code)).post("/api/auth/login/verify");
    }

    /** Turns two-factor sign-in on or off for the signed-in customer. */
    public Response setTwoFactor(String token, boolean enabled, String password) {
        return as(token).body(Map.of("enabled", enabled, "password", password)).put("/api/account/two-factor");
    }

    public Response twoFactorState(String token) {
        return as(token).get("/api/account/two-factor");
    }

    public Response refresh(String refreshToken) {
        return anonymous().body(Map.of("refreshToken", refreshToken)).post("/api/auth/refresh");
    }

    public Response logout(String refreshToken) {
        return anonymous().body(Map.of("refreshToken", refreshToken)).post("/api/auth/logout");
    }

    public Response forgotPassword(String email) {
        return anonymous().body(Map.of("email", email)).post("/api/auth/forgot-password");
    }

    public Response resetPassword(String token, String newPassword) {
        return anonymous().body(Map.of("token", token, "newPassword", newPassword)).post("/api/auth/reset-password");
    }

    /** Registers a fresh customer and returns its JWT. */
    public String registerAndGetToken(String email, String password) {
        return register(email, password).then().statusCode(201).extract().path("token");
    }

    public String adminToken() {
        return login(TestConfig.adminEmail(), TestConfig.adminPassword())
                .then().statusCode(200).extract().path("token");
    }
}
