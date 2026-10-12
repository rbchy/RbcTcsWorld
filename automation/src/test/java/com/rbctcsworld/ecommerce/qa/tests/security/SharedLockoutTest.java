package com.rbctcsworld.ecommerce.qa.tests.security;

import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Two backend instances behind one database and one Redis (CI: 8081 and 8082), as behind a load balancer.
 * With per-instance counting an attacker gets 5 guesses PER INSTANCE; with Redis the 5 are shared.
 * Skipped when no second instance is configured (-DsecondBaseUrl).
 */
@Tag("security")
@Epic("Identity & Access")
@Feature("Brute-force protection")
class SharedLockoutTest {

    private final AuthClient backendA = new AuthClient();
    private AuthClient backendB;

    @BeforeEach
    void secondInstance() {
        assumeFalse(TestConfig.secondBaseUrl().isBlank(), "no second backend instance (-DsecondBaseUrl)");
        backendB = new AuthClient(TestConfig.secondBaseUrl());
    }

    @Test
    @DisplayName("Guesses spread over two backends still lock the account on both (Redis)")
    void lockoutIsShared() {
        String email = TestData.uniqueEmail();
        backendA.register(email, TestData.PASSWORD).then().statusCode(201);
        for (int i = 0; i < 3; i++) backendA.login(email, "Wrong-Pass-" + i).then().statusCode(401);
        for (int i = 0; i < 2; i++) backendB.login(email, "Wrong-Pass-B" + i).then().statusCode(401);

        backendA.login(email, TestData.PASSWORD).then().statusCode(429);
        backendB.login(email, TestData.PASSWORD).then().statusCode(429);
    }

    @Test
    @DisplayName("A refresh token from one backend works on the other (sessions are not tied to an instance)")
    void sessionWorksOnEveryInstance() {
        String refresh = backendA.register(TestData.uniqueEmail(), TestData.PASSWORD)
                .then().statusCode(201).extract().path("refreshToken");
        String next = backendB.refresh(refresh).then().statusCode(200).extract().path("refreshToken");
        backendA.refresh(next).then().statusCode(200);
        backendB.refresh(refresh).then().statusCode(401);           // spent on B earlier: reuse
    }
}
