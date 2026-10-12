package com.rbctcsworld.ecommerce.qa.testdata;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;

import java.math.BigDecimal;

/** Builds test preconditions through the public API (no database shortcuts). */
public final class Fixtures {

    private static final AuthClient AUTH = new AuthClient();
    private static final ProductClient PRODUCTS = new ProductClient();
    private static final CartClient CART = new CartClient();
    private static final OrderClient ORDERS = new OrderClient();
    private static final AdminClient ADMIN = new AdminClient();
    private static String adminToken;
    private static long adminTokenAt;
    private static final long ADMIN_TOKEN_MAX_AGE_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(10);

    private Fixtures() {
    }

    public record Customer(String email, String token) {
    }

    /**
     * Cached for speed, renewed after 10 minutes: access tokens live 15 minutes and a full run (Safari,
     * mutation, k6) takes longer than that.
     */
    public static synchronized String adminToken() {
        if (adminToken == null || System.nanoTime() - adminTokenAt > ADMIN_TOKEN_MAX_AGE_NANOS) {
            adminToken = AUTH.adminToken();
            adminTokenAt = System.nanoTime();
        }
        return adminToken;
    }

    public static Customer newCustomer() {
        String email = TestData.uniqueEmail();
        return new Customer(email, AUTH.registerAndGetToken(email, TestData.PASSWORD));
    }

    /** New product with a known price and stock, so totals and stock maths are predictable. */
    public static long product(String price, int stock) {
        int id = PRODUCTS.create(adminToken(),
                        ProductClient.body("QA Item", TestData.uniqueSku(), "qa", new BigDecimal(price), stock))
                .then().statusCode(201).extract().path("id");
        return id;
    }

    public record Product(long id, String sku, String name) {
    }

    /**
     * Like {@link #product} but the name contains the unique SKU, so a UI test can search for exactly
     * this product in the storefront grid.
     */
    public static Product namedProduct(String price, int stock) {
        String sku = TestData.uniqueSku();
        String name = "UI Item " + sku;
        int id = PRODUCTS.create(adminToken(),
                        ProductClient.body(name, sku, "qa", new BigDecimal(price), stock))
                .then().statusCode(201).extract().path("id");
        return new Product(id, sku, name);
    }

    /** Customer orders {@code qty} of the product and pays with the approved test card. Returns the order id. */
    public static long paidOrder(Customer c, long productId, int qty) {
        CART.add(c.token(), productId, qty).then().statusCode(201);
        int id = ORDERS.place(c.token()).then().statusCode(201).extract().path("id");
        ORDERS.pay(c.token(), id, TestData.CARD_OK).then().statusCode(200);
        return id;
    }

    /** Customer places an order for the product but does not pay. Returns the order id. */
    public static long placedOrder(Customer c, long productId, int qty) {
        CART.add(c.token(), productId, qty).then().statusCode(201);
        int id = ORDERS.place(c.token()).then().statusCode(201).extract().path("id");
        return id;
    }

    /** paidOrder + warehouse ships (UPS). */
    public static long shippedOrder(Customer c, long productId, int qty) {
        long id = paidOrder(c, productId, qty);
        ADMIN.ship(adminToken(), id, "UPS").then().statusCode(200);
        return id;
    }

    /** A new customer who has received (DELIVERED) one unit of the product - the only kind allowed to review it. */
    public static Customer verifiedBuyer(long productId) {
        Customer c = newCustomer();
        deliveredOrder(c, productId, 1);
        return c;
    }

    /** paidOrder + warehouse ships (UPS) and marks delivered. */
    public static long deliveredOrder(Customer c, long productId, int qty) {
        long id = paidOrder(c, productId, qty);
        ADMIN.ship(adminToken(), id, "UPS").then().statusCode(200);
        ADMIN.deliver(adminToken(), id).then().statusCode(200);
        return id;
    }
}
