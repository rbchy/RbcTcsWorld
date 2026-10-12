package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.CheckoutClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.api.ReviewClient;
import com.rbctcsworld.ecommerce.qa.api.WishlistClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

/**
 * API contract tests: every response shape the storefront (and any other client) relies on is checked against a
 * JSON Schema in src/test/resources/contracts. Required fields, types, formats and enums are pinned, and
 * additionalProperties=false makes any new, renamed or leaked field fail - so a contract change is always a
 * deliberate edit of the schema in the same pull request. The privacy rules are part of the contract:
 * public tracking has no customer data, reviewers are masked, payments carry only the last 4 card digits.
 * The provider side (OpenAPI breaking-change gate) runs in CI: .github/scripts/openapi-breaking-changes.sh.
 */
@Tag("api")
@Tag("contract")
@Epic("API contract")
@Feature("Response schemas")
class ApiContractTest {

    private static final AuthClient auth = new AuthClient();
    private static final ProductClient products = new ProductClient();
    private static final CartClient cart = new CartClient();
    private static final CheckoutClient checkout = new CheckoutClient();
    private static final OrderClient orders = new OrderClient();
    private static final ReviewClient reviews = new ReviewClient();
    private static final WishlistClient wishlist = new WishlistClient();
    private static final AdminClient admin = new AdminClient();

    private static Fixtures.Product product;
    private static Customer buyer;

    @BeforeAll
    static void data() {
        product = Fixtures.namedProduct("21.50", 50);
        buyer = Fixtures.verifiedBuyer(product.id());          // has a DELIVERED order, may review
        reviews.create(buyer.token(), product.id(), 4).then().statusCode(201);
    }

    private static void matches(Response r, int status, String contract) {
        r.then().statusCode(status).body(matchesJsonSchemaInClasspath("contracts/" + contract + ".json"));
    }

    @Test
    @DisplayName("Auth: register, login and refresh return both tokens, e-mail, role and expiresIn")
    void authContract() {
        String email = TestData.uniqueEmail();
        matches(auth.register(email, TestData.PASSWORD), 201, "auth");
        Response login = auth.login(email, TestData.PASSWORD);
        matches(login, 200, "auth");
        matches(auth.refresh(login.path("refreshToken")), 200, "auth");
    }

    @Test
    @DisplayName("Catalog: product, product page and the paging headers")
    void catalogContract() {
        matches(products.get(product.id()), 200, "product");
        Response page = products.page(null, 0, 5, "id");
        matches(page, 200, "product-list");
        page.then()
                .header("X-Total-Count", matchesPattern("\\d+"))
                .header("X-Total-Pages", matchesPattern("\\d+"))
                .header("X-Page", "0")
                .header("X-Page-Size", "5")
                .header("Link", notNullValue());
    }

    @Test
    @DisplayName("Cart, checkout quote and order through payment, tracking and payment history")
    void purchaseContract() {
        Customer c = Fixtures.newCustomer();
        matches(cart.add(c.token(), product.id(), 2), 201, "cart");
        matches(cart.get(c.token()), 200, "cart");
        matches(checkout.quote(c.token(), null), 200, "quote");

        Response placed = orders.place(c.token());
        matches(placed, 201, "order");
        long orderId = placed.then().extract().<Integer>path("id");

        matches(orders.pay(c.token(), orderId, TestData.CARD_OK), 200, "order");
        matches(orders.get(c.token(), orderId), 200, "order");
        matches(orders.mine(c.token()), 200, "order-list");
        matches(orders.payments(c.token(), orderId), 200, "payment-list");

        admin.ship(Fixtures.adminToken(), orderId, "UPS").then().statusCode(200);
        matches(orders.tracking(c.token(), orderId), 200, "tracking");
        String trackingNumber = orders.get(c.token(), orderId).then().extract().path("trackingNumber");
        matches(orders.publicTracking(trackingNumber), 200, "tracking");   // public: same shape, no customer data
    }

    @Test
    @DisplayName("Public reviews of a product: masked reviewer names, all five star buckets")
    void reviewsContract() {
        matches(reviews.forProduct(product.id()), 200, "product-reviews");
    }

    @Test
    @DisplayName("Wishlist with price-drop data")
    void wishlistContract() {
        wishlist.add(buyer.token(), product.id()).then().statusCode(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(201)));
        matches(wishlist.get(buyer.token()), 200, "wishlist");
    }

    @Test
    @DisplayName("Errors: one JSON shape for 400, 401, 403, 404 and 409")
    void errorContract() {
        String customer = Fixtures.newCustomer().token();
        matches(products.get(99_999_999L), 404, "error");
        matches(auth.login(TestData.uniqueEmail(), "wrong-password"), 401, "error");
        matches(cart.get(null), 401, "error");
        matches(products.create(customer, ProductClient.body("x", "SKU-" + System.nanoTime(), "qa",
                java.math.BigDecimal.ONE, 1)), 403, "error");
        matches(cart.add(customer, product.id(), 0), 400, "error");   // validation: fieldErrors filled
        matches(orders.place(customer), 400, "error");                // empty cart -> business rule
        matches(auth.register(buyer.email(), TestData.PASSWORD), 409, "error");
    }
}
