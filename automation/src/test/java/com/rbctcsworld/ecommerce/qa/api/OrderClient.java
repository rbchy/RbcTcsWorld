package com.rbctcsworld.ecommerce.qa.api;

import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

public class OrderClient extends ApiClient {

    /** Places the cart as an order, delivered to TestData.ADDRESS. */
    public Response place(String token) {
        return as(token).body(Map.of("shippingAddress", TestData.ADDRESS)).post("/api/orders");
    }

    public Response place(String token, String couponCode) {
        return as(token).body(Map.of("couponCode", couponCode, "shippingAddress", TestData.ADDRESS)).post("/api/orders");
    }

    /** Any body, for the negative tests (no address, wrong fields). */
    public Response placeWithBody(String token, Object body) {
        return as(token).body(body).post("/api/orders");
    }

    public Response pay(String token, long orderId, String cardNumber, Integer expiryMonth, Integer expiryYear, String cvv) {
        Map<String, Object> body = new HashMap<>();   // HashMap allows nulls for negative tests
        body.put("cardNumber", cardNumber);
        body.put("expiryMonth", expiryMonth);
        body.put("expiryYear", expiryYear);
        body.put("cvv", cvv);
        return as(token).body(body).post("/api/orders/{id}/pay", orderId);
    }

    /** Pays with a valid, approved test card. */
    public Response pay(String token, long orderId, String cardNumber) {
        return pay(token, orderId, cardNumber, 12, 2035, "123");
    }

    public Response tracking(String token, long orderId) {
        return as(token).get("/api/orders/{id}/tracking", orderId);
    }

    public Response requestReturn(String token, long orderId, String reason) {
        return as(token).body(Map.of("reason", reason)).post("/api/orders/{id}/return", orderId);
    }

    public Response getReturn(String token, long orderId) {
        return as(token).get("/api/orders/{id}/return", orderId);
    }

    /** PUBLIC carrier-style tracking page - no token. */
    public Response publicTracking(String trackingNumber) {
        return anonymous().get("/api/tracking/{tn}", trackingNumber);
    }

    public Response payments(String token, long orderId) {
        return as(token).get("/api/orders/{id}/payments", orderId);
    }

    public Response mine(String token) {
        return as(token).get("/api/orders");
    }

    public Response get(String token, long orderId) {
        return as(token).get("/api/orders/{id}", orderId);
    }

    public Response cancel(String token, long orderId) {
        return as(token).post("/api/orders/{id}/cancel", orderId);
    }
}
