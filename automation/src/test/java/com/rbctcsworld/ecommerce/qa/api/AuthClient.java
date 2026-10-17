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
