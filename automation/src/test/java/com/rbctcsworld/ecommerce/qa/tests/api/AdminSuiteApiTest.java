package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Store admin back office: customers, coupon editing, reports, payment ledger and advanced catalog search. */
@Tag("api")
@Tag("admin")
@Epic("Store admin")
@Feature("Back office")
class AdminSuiteApiTest {

    private final AdminClient admin = new AdminClient();
    private final AuthClient auth = new AuthClient();
    private final CartClient cart = new CartClient();
    private final ProductClient products = new ProductClient();

    private long customerId(String email) {
        return admin.customers(Fixtures.adminToken(), Map.of("q", email)).then().statusCode(200)
                .body("total", equalTo(1)).extract().<Integer>path("items[0].id");
    }

    @Test
    @Tag("smoke")
    @DisplayName("Customers: found by part of the e-mail, with orders and money spent; no password in the answer")
    void customerSearchAndDetail() {
        Customer c = Fixtures.newCustomer();
        Fixtures.paidOrder(c, Fixtures.product("10.00", 5), 1);
        long id = customerId(c.email().substring(0, 18).toUpperCase());

        admin.customers(Fixtures.adminToken(), Map.of("q", c.email())).then()
                .body("items[0].orders", equalTo(1))
                .body("items[0].active", equalTo(true))
                .body("items[0]", not(org.hamcrest.Matchers.hasKey("password")));
        admin.customer(Fixtures.adminToken(), id).then().statusCode(200)
                .body("customer.email", equalTo(c.email()))
                .body("orders", hasSize(1))
                .body("lastAddress.zip", org.hamcrest.Matchers.notNullValue());
        admin.customers(Fixtures.adminToken(), Map.of("sort", "salary")).then().statusCode(400);
    }

    @Test
    @Tag("security")
    @DisplayName("SECURITY: a disabled customer is locked out at once (token, refresh, login) and can come back")
    void disableAndEnable() {
        String email = TestData.uniqueEmail();
        var registered = auth.register(email, TestData.PASSWORD).then().statusCode(201).extract();
        String token = registered.path("token");
        String refresh = registered.path("refreshToken");
        long id = customerId(email);

        admin.setCustomerActive(Fixtures.adminToken(), id, false).then().statusCode(200).body("active", equalTo(false));
        cart.get(token).then().statusCode(401);
        auth.refresh(refresh).then().statusCode(401);
        auth.login(email, TestData.PASSWORD).then().statusCode(403)
                .body("message", equalTo("This account is disabled. Please contact the store."));

        admin.setCustomerActive(Fixtures.adminToken(), id, true).then().statusCode(200).body("active", equalTo(true));
        auth.login(email, TestData.PASSWORD).then().statusCode(200);
    }

    @Test
    @Tag("security")
    @DisplayName("SECURITY: customers cannot reach customers, reports or payments")
    void customersAreRefused() {
        Customer c = Fixtures.newCustomer();
        admin.customers(c.token(), Map.of()).then().statusCode(403);
        admin.report(c.token(), "sales", Map.of()).then().statusCode(403);
        admin.payments(c.token(), Map.of()).then().statusCode(403);
        admin.updateCoupon(c.token(), 1, Map.of("type", "FIXED", "value", 1)).then().statusCode(403);
    }

    @Test
    @DisplayName("Coupons: edit the terms (code stays), switch off and on, find by code and state")
    void couponLifecycle() {
        String code = "QA" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long id = admin.createCoupon(Fixtures.adminToken(), Map.of("code", code, "type", "FIXED", "value", 5, "maxUses", 3))
                .then().statusCode(201).extract().<Integer>path("id");

        admin.updateCoupon(Fixtures.adminToken(), id, Map.of("type", "PERCENT", "value", 15, "minOrderAmount", 25))
                .then().statusCode(200).body("code", equalTo(code)).body("type", equalTo("PERCENT"));
        admin.updateCoupon(Fixtures.adminToken(), id, Map.of("type", "PERCENT", "value", 101)).then().statusCode(400);
        admin.setCouponActive(Fixtures.adminToken(), id, false).then().statusCode(200).body("active", equalTo(false));
        admin.coupons(Fixtures.adminToken(), Map.of("q", code.toLowerCase(), "status", "DISABLED"))
                .then().statusCode(200).body("code", contains(code));
        admin.setCouponActive(Fixtures.adminToken(), id, true).then().body("active", equalTo(true));
        admin.coupons(Fixtures.adminToken(), Map.of("q", code, "status", "ACTIVE")).then().body("code", contains(code));
        admin.coupons(Fixtures.adminToken(), Map.of("status", "OLD")).then().statusCode(400);
    }

    @Test
    @Tag("smoke")
    @DisplayName("Reports: today's sale is in sales, best sellers and low stock; every report exports as CSV")
    void reports() {
        long productId = Fixtures.product("10.00", 2);
        String sku = products.get(productId).then().extract().path("sku");
        Fixtures.paidOrder(Fixtures.newCustomer(), productId, 1);
        // yesterday..tomorrow: the test machine and the server may be in different time zones
        String from = LocalDate.now().minusDays(1).toString();
        String to = LocalDate.now().plusDays(1).toString();

        admin.report(Fixtures.adminToken(), "sales", Map.of("from", from, "to", to)).then().statusCode(200)
                .body("columns[0]", equalTo("day")).body("rows", hasSize(3)).body("totals[0]", equalTo("total"));
        admin.report(Fixtures.adminToken(), "top-products", Map.of("from", from, "to", to, "limit", 100))
                .then().body("rows.collect { it[0] }", hasItem(sku));
        admin.report(Fixtures.adminToken(), "low-stock", Map.of("threshold", 1))
                .then().body("rows.collect { it[0] }", hasItem(sku));
        for (String name : List.of("sales", "top-products", "orders-by-status", "returns", "coupons", "low-stock", "inventory")) {
            String csv = admin.reportCsv(Fixtures.adminToken(), name, Map.of()).then().statusCode(200)
                    .header("Content-Disposition", containsString("report-" + name)).extract().asString();
            assertTrue(csv.startsWith("\""), name + " starts with its header row");
        }
        admin.report(Fixtures.adminToken(), "sales", Map.of("from", "2026-02-01", "to", "2026-01-01")).then().statusCode(400);
        admin.report(Fixtures.adminToken(), "salary", Map.of()).then().statusCode(404);
    }

    @Test
    @DisplayName("Payment ledger: the charge of an order is found by its number, with only the last 4 card digits")
    void paymentLedger() {
        Customer c = Fixtures.newCustomer();
        long order = Fixtures.paidOrder(c, Fixtures.product("10.00", 5), 1);
        String number = new com.rbctcsworld.ecommerce.qa.api.OrderClient().get(c.token(), order).then().extract().path("orderNumber");

        admin.payments(Fixtures.adminToken(), Map.of("q", number)).then().statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].type", equalTo("CHARGE"))
                .body("items[0].cardLast4", equalTo("4242"))
                .body("items[0]", not(org.hamcrest.Matchers.hasKey("cardNumber")));
        admin.payments(Fixtures.adminToken(), Map.of("type", "GIFT")).then().statusCode(400);
    }

    @Test
    @DisplayName("Advanced catalog search: category, price range and in stock only combine with paging")
    void advancedProductSearch() {
        String category = "qa-" + UUID.randomUUID().toString().substring(0, 8);
        for (String[] p : new String[][]{{"5.00", "3"}, {"20.00", "0"}, {"80.00", "1"}}) {
            products.create(Fixtures.adminToken(), ProductClient.body("Adv " + p[0], TestData.uniqueSku(), category,
                    new BigDecimal(p[0]), Integer.parseInt(p[1]))).then().statusCode(201);
        }
        products.search(Map.of("category", category.toUpperCase(), "sort", "price_asc")).then().statusCode(200)
                .header("X-Total-Count", "3").body("price", contains(5.0f, 20.0f, 80.0f));
        products.search(Map.of("category", category, "minPrice", "5", "maxPrice", "20")).then().header("X-Total-Count", "2");
        products.search(Map.of("category", category, "inStock", true)).then().header("X-Total-Count", "2");
        products.search(Map.of("minPrice", "10", "maxPrice", "5")).then().statusCode(400);
        products.categories().then().statusCode(200).body("$", hasItem(category));
    }

    @Test
    @DisplayName("Similar products: same category, in stock first, then the closest price; limit 1..12")
    void similarProducts() {
        String category = "sim-" + UUID.randomUUID().toString().substring(0, 8);
        long[] ids = new long[3];
        String[][] rows = {{"10.00", "5"}, {"12.00", "0"}, {"30.00", "2"}};
        for (int i = 0; i < 3; i++) {
            ids[i] = products.create(Fixtures.adminToken(), ProductClient.body("Sim " + i, TestData.uniqueSku(), category,
                    new BigDecimal(rows[i][0]), Integer.parseInt(rows[i][1]))).then().statusCode(201).extract().<Integer>path("id");
        }
        products.similar(ids[0], null).then().statusCode(200)
                .body("id", contains((int) ids[2], (int) ids[1]));        // 12.00 is closer but sold out
        products.similar(ids[0], 13).then().statusCode(400);
        products.similar(999_999_999L, null).then().statusCode(404);
    }
}
