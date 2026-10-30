package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;

/** Flash deals: a price below the normal price between two UTC times (REQ-CAT-14). */
@Tag("api")
@Tag("catalog")
@Epic("Catalog")
@Feature("Flash deals")
class FlashDealApiTest {

    private final ProductClient products = new ProductClient();
    private final CartClient cart = new CartClient();
    private final String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    private static String utc(double hoursFromNow) {
        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(Math.round(hoursFromNow * 60)).withNano(0).toString();
    }

    /**
     * /api/products/deals lists at most 12 deals, the one ending first on top. On a database that keeps the deals of
     * earlier runs (a local `make app` used all evening), a deal ending in hours is pushed off the list by older ones:
     * the deal that must appear there ends in 20 minutes, sooner than any deal another run left behind.
     */
    static final double ENDS_SOON = 20 / 60.0;

    private Map<String, Object> deal(String sku, String price, String dealPrice, double startH, double endH) {
        Map<String, Object> b = new HashMap<>(ProductClient.body("Deal Lamp " + tag, sku, "home", new BigDecimal(price), 5));
        if (dealPrice != null) b.put("dealPrice", new BigDecimal(dealPrice));
        b.put("dealStartsAt", utc(startH));
        b.put("dealEndsAt", utc(endH));
        return b;
    }

    @Test
    @Tag("smoke")
    @DisplayName("A running deal shows in /deals, the cart charges the deal price and the product says so")
    void runningDeal() {
        long id = products.create(Fixtures.adminToken(), deal("DL-" + tag, "40.00", "25.00", -1, ENDS_SOON))
                .then().statusCode(201).body("dealLive", equalTo(true)).body("dealPrice", equalTo(25.0f))
                .extract().jsonPath().getLong("id");

        products.anonymousGet("/api/products/deals").then().statusCode(200).body("id", hasItem((int) id));

        Customer c = Fixtures.newCustomer();
        cart.add(c.token(), id, 2).then().statusCode(201).body("subtotal", equalTo(50.0f));
    }

    @Test
    @DisplayName("A deal that has not started yet is not live, not in /deals and the cart charges the normal price")
    void futureDeal() {
        long id = products.create(Fixtures.adminToken(), deal("DF-" + tag, "40.00", "25.00", 3, 5))
                .then().statusCode(201).body("dealLive", equalTo(false)).extract().jsonPath().getLong("id");

        products.anonymousGet("/api/products/deals").then().statusCode(200).body("id", not(hasItem((int) id)));
        cart.add(Fixtures.newCustomer().token(), id, 1).then().statusCode(201).body("subtotal", equalTo(40.0f));
    }

    @Test
    @DisplayName("An ordinary product has no deal fields at all in its JSON")
    void noDealNoFields() {
        long id = products.create(Fixtures.adminToken(), ProductClient.body("Plain " + tag, "PL-" + tag, "home", new BigDecimal("9.99"), 3))
                .then().statusCode(201).extract().jsonPath().getLong("id");
        products.get(id).then().statusCode(200).body("$", not(hasKey("dealPrice"))).body("$", not(hasKey("dealLive")));
    }

    @Test
    @DisplayName("Broken deals are 400: price not lower, end before start, half a deal, already over, longer than 30 days")
    void brokenDeals() {
        String admin = Fixtures.adminToken();
        products.create(admin, deal("B1-" + tag, "40.00", "40.00", -1, 2)).then().statusCode(400).body("message", containsString("lower than the price"));
        products.create(admin, deal("B2-" + tag, "40.00", "30.00", 2, 1)).then().statusCode(400).body("message", containsString("end after it starts"));
        products.create(admin, deal("B3-" + tag, "40.00", null, -1, 2)).then().statusCode(400).body("message", containsString("needs a deal price"));
        products.create(admin, deal("B4-" + tag, "40.00", "30.00", -5, -2)).then().statusCode(400).body("message", containsString("already ended"));
        products.create(admin, deal("B5-" + tag, "40.00", "30.00", 0, 24 * 31)).then().statusCode(400).body("message", containsString("30 days"));
    }

    @Test
    @DisplayName("Saving the product again without deal fields ends the deal")
    void updateWithoutDealClearsIt() {
        long id = products.create(Fixtures.adminToken(), deal("DU-" + tag, "40.00", "25.00", -1, 2))
                .then().statusCode(201).extract().jsonPath().getLong("id");
        products.update(Fixtures.adminToken(), id, ProductClient.body("Deal Lamp " + tag, "DU-" + tag, "home", new BigDecimal("40.00"), 5))
                .then().statusCode(200).body("$", not(hasKey("dealPrice")));
        products.anonymousGet("/api/products/deals").then().statusCode(200).body("id", not(hasItem((int) id)));
    }
}
