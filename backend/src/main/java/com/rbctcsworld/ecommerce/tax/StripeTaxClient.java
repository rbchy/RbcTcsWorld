package com.rbctcsworld.ecommerce.tax;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The two Stripe Tax calls, over plain HTTP like the payment client:
 *  - POST /v1/tax/calculations                          the tax for the lines, shipping and delivery address
 *  - POST /v1/tax/transactions/create_from_calculation  after payment: the sale goes into Stripe's tax report
 *  - POST /v1/tax/transactions/create_reversal          after a refund: all of the sale, or a flat amount of it, out
 * Amounts in cents, form encoded, an Idempotency-Key on every POST.
 */
class StripeTaxClient {

    /** id: taxcalc_...; taxCents: tax_amount_exclusive (added on top of the prices). */
    record Calculation(String id, long taxCents, long totalCents) {
    }

    record Transaction(String id, String reference) {
    }

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final URI base;
    private final String secretKey;
    private final Duration timeout;
    private final HttpClient http;

    StripeTaxClient(String baseUrl, String secretKey, Duration timeout) {
        this.base = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.secretKey = secretKey;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
    }

    /** lines: (reference, cents, quantity); exclusive = the tax comes on top of the price, as US shops show it. */
    Calculation calculate(String currency, List<TaxCalculator.TaxLine> lines, long shippingCents,
                          TaxCalculator.TaxAddress address, String idempotencyKey) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("currency", currency);
        for (int i = 0; i < lines.size(); i++) {
            TaxCalculator.TaxLine l = lines.get(i);
            form.put("line_items[" + i + "][amount]", Long.toString(cents(l.amount())));
            form.put("line_items[" + i + "][reference]", l.reference());
            form.put("line_items[" + i + "][quantity]", Integer.toString(l.quantity()));
            form.put("line_items[" + i + "][tax_behavior]", "exclusive");
        }
        form.put("shipping_cost[amount]", Long.toString(shippingCents));
        form.put("customer_details[address][line1]", address.line1());
        form.put("customer_details[address][city]", address.city());
        form.put("customer_details[address][state]", address.state());
        form.put("customer_details[address][postal_code]", address.zip());
        form.put("customer_details[address][country]", "US");
        form.put("customer_details[address_source]", "shipping");
        JsonNode n = post("/v1/tax/calculations", form, idempotencyKey);
        return new Calculation(text(n, "id"), n.path("tax_amount_exclusive").asLong(), n.path("amount_total").asLong());
    }

    Transaction recordSale(String calculationId, String reference, String idempotencyKey) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("calculation", calculationId);
        form.put("reference", reference);
        JsonNode n = post("/v1/tax/transactions/create_from_calculation", form, idempotencyKey);
        return new Transaction(text(n, "id"), text(n, "reference"));
    }

    /**
     * flatCents null: the whole sale (mode=full). Otherwise mode=partial with flat_amount, which Stripe wants
     * negative and tax included, and spreads over the lines and shipping of the sale.
     */
    Transaction reverse(String transactionId, String reference, Long flatCents, String idempotencyKey) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", flatCents == null ? "full" : "partial");
        form.put("original_transaction", transactionId);
        form.put("reference", reference);
        if (flatCents != null) {
            form.put("flat_amount", Long.toString(-Math.abs(flatCents)));
        }
        JsonNode n = post("/v1/tax/transactions/create_reversal", form, idempotencyKey);
        return new Transaction(text(n, "id"), text(n, "reference"));
    }

    static long cents(java.math.BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private JsonNode post(String path, Map<String, String> form, String idempotencyKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(encode(form))).build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new StripeTaxException(0, "api_connection_error", "Stripe not reachable: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StripeTaxException(0, "api_connection_error", "Interrupted");
        }
        JsonNode body;
        try {
            body = JSON.readTree(response.body());
        } catch (JacksonException e) {
            throw new StripeTaxException(response.statusCode(), "api_error",
                    "Unreadable answer from Stripe (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() / 100 == 2) {
            return body;
        }
        JsonNode error = body.path("error");
        throw new StripeTaxException(response.statusCode(), text(error, "code"), text(error, "message"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString() : null;
    }
}
