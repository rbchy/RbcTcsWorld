package com.rbctcsworld.ecommerce.fulfillment;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The three Shippo calls, over plain HTTP + JSON (no SDK), so the tests can check exactly what is sent:
 *  - POST /shipments/                     the parcel, from and to: Shippo answers with every carrier's rates
 *  - POST /transactions/                  buys the label for one rate: tracking number + label PDF
 *  - GET  /tracks/{carrier}/{number}      where the parcel is now
 * Authorization: "ShippoToken shippo_test_..." (test mode: real rates and sample labels, no charge).
 */
class ShippoClient {

    record Rate(String id, String provider, String service, BigDecimal amount, String currency) {
    }

    /** status SUCCESS: a label; ERROR: messages says why. */
    record Transaction(String id, String status, String trackingNumber, String labelUrl, String messages) {
    }

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final URI base;
    private final String token;
    private final Duration timeout;
    private final HttpClient http;

    ShippoClient(String baseUrl, String token, Duration timeout) {
        this.base = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.token = token;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
    }

    /** One parcel; async=false: the rates come in this answer. */
    List<Rate> rates(ShippingProvider.Address from, ShippingProvider.Address to, Parcel parcel, String metadata) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("length", parcel.length());
        p.put("width", parcel.width());
        p.put("height", parcel.height());
        p.put("distance_unit", "in");
        p.put("weight", parcel.weightOz());
        p.put("mass_unit", "oz");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("address_from", address(from));
        body.put("address_to", address(to));
        body.put("parcels", List.of(p));
        body.put("async", false);
        body.put("metadata", metadata);
        JsonNode n = post("/shipments/", body);
        List<Rate> rates = new ArrayList<>();
        for (JsonNode r : n.path("rates")) {
            if (!r.path("amount").isString()) {
                continue;                       // a rate without a price is never "the cheapest"
            }
            rates.add(new Rate(text(r, "object_id"), text(r, "provider"), text(r.path("servicelevel"), "name"),
                    new BigDecimal(r.path("amount").asString()), text(r, "currency")));
        }
        return rates;
    }

    Transaction buy(String rateId, String metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rate", rateId);
        body.put("label_file_type", "PDF");
        body.put("async", false);
        body.put("metadata", metadata);
        JsonNode n = post("/transactions/", body);
        List<String> texts = new ArrayList<>();
        for (JsonNode m : n.path("messages")) {
            String t = text(m, "text");
            if (t != null) texts.add(t);
        }
        return new Transaction(text(n, "object_id"), text(n, "status"), text(n, "tracking_number"), text(n, "label_url"),
                String.join("; ", texts));
    }

    /** DELIVERED, TRANSIT, PRE_TRANSIT, RETURNED, FAILURE, UNKNOWN; null without a status. */
    String trackingStatus(String carrier, String trackingNumber) {
        JsonNode n = send(HttpRequest.newBuilder(URI.create(base + "/tracks/" + enc(carrier) + "/" + enc(trackingNumber))).GET());
        return text(n.path("tracking_status"), "status");
    }

    record Parcel(String length, String width, String height, String weightOz) {
    }

    private static Map<String, Object> address(ShippingProvider.Address a) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("name", a.name());
        n.put("street1", a.street1());
        if (a.street2() != null) n.put("street2", a.street2());
        n.put("city", a.city());
        n.put("state", a.state());
        n.put("zip", a.zip());
        n.put("country", "US");
        if (a.phone() != null) n.put("phone", a.phone());
        return n;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private JsonNode send(HttpRequest.Builder builder) {
        HttpRequest request = builder.timeout(timeout).header("Authorization", "ShippoToken " + token).build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ShippoException(0, "Shippo not reachable: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShippoException(0, "Interrupted");
        }
        JsonNode body;
        try {
            body = JSON.readTree(response.body());
        } catch (JacksonException e) {
            throw new ShippoException(response.statusCode(), "Unreadable answer from Shippo (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() / 100 == 2) {
            return body;
        }
        // Shippo error bodies differ by endpoint: {"detail": "..."} or {"field": ["..."]}; keep them short and readable
        String detail = text(body, "detail");
        throw new ShippoException(response.statusCode(), detail != null ? detail : abbreviate(body.toString()));
    }

    private static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString() : null;
    }
}
