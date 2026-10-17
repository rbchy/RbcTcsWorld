package com.rbctcsworld.ecommerce.qa.api;

import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/** The customer's address book: /api/addresses. */
public class AddressClient extends ApiClient {

    public Response list(String token) {
        return as(token).get("/api/addresses");
    }

    public Response add(String token, Object body) {
        return as(token).body(body).post("/api/addresses");
    }

    public Response update(String token, long id, Object body) {
        return as(token).body(body).put("/api/addresses/{id}", id);
    }

    public Response makeDefault(String token, long id) {
        return as(token).post("/api/addresses/{id}/default", id);
    }

    public Response delete(String token, long id) {
        return as(token).delete("/api/addresses/{id}", id);
    }

    /** {label, address: {...TestData.ADDRESS with this street}, makeDefault}. */
    public static Map<String, Object> body(String label, String line1, boolean makeDefault) {
        Map<String, Object> address = new LinkedHashMap<>(com.rbctcsworld.ecommerce.qa.testdata.TestData.ADDRESS);
        address.put("line1", line1);
        Map<String, Object> m = new LinkedHashMap<>();
        if (label != null) m.put("label", label);
        m.put("address", address);
        m.put("makeDefault", makeDefault);
        return m;
    }
}
