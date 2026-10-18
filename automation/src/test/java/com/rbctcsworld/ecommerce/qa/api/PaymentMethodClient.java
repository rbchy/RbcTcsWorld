package com.rbctcsworld.ecommerce.qa.api;

import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

/** /api/payment-methods: the customer's wallet of saved cards. */
public class PaymentMethodClient extends ApiClient {

    public Response list(String token) {
        return as(token).get("/api/payment-methods");
    }

    public Response add(String token, String cardNumber, Integer month, Integer year, boolean makeDefault) {
        Map<String, Object> body = new HashMap<>();
        body.put("cardNumber", cardNumber);
        body.put("expiryMonth", month);
        body.put("expiryYear", year);
        body.put("makeDefault", makeDefault);
        return as(token).body(body).post("/api/payment-methods");
    }

    public Response makeDefault(String token, long id) {
        return as(token).post("/api/payment-methods/{id}/default", id);
    }

    public Response delete(String token, long id) {
        return as(token).delete("/api/payment-methods/{id}", id);
    }
}
