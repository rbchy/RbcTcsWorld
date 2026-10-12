package com.rbctcsworld.ecommerce.qa.api;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Base for all API clients: base URL, JSON, optional bearer token, request/response logged on failure,
 * and every call attached to the Allure report (method, URL, headers, body, status, response).
 */
public abstract class ApiClient {

    private static final AllureRestAssured ALLURE = new AllureRestAssured();

    private final String baseUri;

    protected ApiClient() {
        this(TestConfig.baseUrl());
    }

    /** Another backend instance, e.g. the second one behind the same database and Redis. */
    protected ApiClient(String baseUri) {
        this.baseUri = baseUri;
    }

    protected RequestSpecification anonymous() {
        return given().spec(new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(ALLURE)
                .build())
                .log().ifValidationFails(LogDetail.ALL);
    }

    protected RequestSpecification as(String token) {
        RequestSpecification spec = anonymous();
        return token == null ? spec : spec.header("Authorization", "Bearer " + token);
    }
}
