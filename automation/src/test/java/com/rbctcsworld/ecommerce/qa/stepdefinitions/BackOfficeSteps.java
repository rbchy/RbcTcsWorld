package com.rbctcsworld.ecommerce.qa.stepdefinitions;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import com.rbctcsworld.ecommerce.qa.context.ScenarioContext;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Steps for the back office: warehouses, customers, coupons, reports and the audit trail. */
public class BackOfficeSteps {

    private final ScenarioContext ctx;
    private final AdminClient admin = new AdminClient();
    private final AuthClient auth = new AuthClient();
    private final ProductClient products = new ProductClient();

    public BackOfficeSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    private long warehouse(String code) {
        List<Map<String, Object>> all = admin.warehouses(Fixtures.adminToken()).then().statusCode(200).extract().jsonPath().getList("");
        return all.stream().filter(w -> code.equals(w.get("code"))).map(w -> ((Number) w.get("id")).longValue())
                .findFirst().orElseThrow(() -> new IllegalStateException("No warehouse " + code));
    }

    private int held(String code) {
        long productId = ctx.get("productId");
        JsonPath s = admin.productStock(Fixtures.adminToken(), productId).then().statusCode(200).extract().jsonPath();
        return s.getInt("warehouses.find { it.code == '" + code + "' }.quantity");
    }

    // ---------------------------------------------------------------- warehouses

    @Given("warehouse {string} receives {int} units of that product")
    public void receive(String code, int units) {
        long productId = ctx.get("productId");
        admin.adjustStock(Fixtures.adminToken(), productId, warehouse(code), units, "RECEIVED").then().statusCode(200);
    }

    @Then("the order ships {int} from {string} and {int} from {string}")
    public void shipsFrom(int q1, String w1, int q2, String w2) {
        long orderId = ctx.get("orderId");
        admin.allocations(Fixtures.adminToken(), orderId).then().statusCode(200)
                .body("warehouseCode", contains(w1, w2))
                .body("quantity", contains(q1, q2));
    }

    @Then("warehouse {string} holds {int} and warehouse {string} holds {int} of that product")
    public void holds(String w1, int q1, String w2, int q2) {
        assertEquals(q1, held(w1), w1);
        assertEquals(q2, held(w2), w2);
    }

    @When("the admin moves {int} units of that product from {string} to {string}")
    public void move(int qty, String from, String to) {
        long productId = ctx.get("productId");
        ctx.lastResponse(admin.transferStock(Fixtures.adminToken(), productId, warehouse(from), warehouse(to), qty));
    }

    @When("the admin writes off {int} damaged unit(s) of that product in {string}")
    public void damaged(int units, String code) {
        long productId = ctx.get("productId");
        ctx.lastResponse(admin.adjustStock(Fixtures.adminToken(), productId, warehouse(code), -units, "DAMAGED"));
    }

    // ---------------------------------------------------------------- customers

    @Given("a customer account exists")
    public void customerAccount() {
        String email = TestData.uniqueEmail();
        auth.register(email, TestData.PASSWORD).then().statusCode(201);
        ctx.put("email", email);
        long id = admin.customers(Fixtures.adminToken(), Map.of("q", email)).then().statusCode(200)
                .extract().<Integer>path("items[0].id");
        ctx.put("customerId", id);
    }

    @When("the admin disables that customer")
    public void disable() {
        long id = ctx.get("customerId");
        admin.setCustomerActive(Fixtures.adminToken(), id, false).then().statusCode(200).body("active", equalTo(false));
    }

    @When("the admin enables that customer")
    public void enable() {
        long id = ctx.get("customerId");
        admin.setCustomerActive(Fixtures.adminToken(), id, true).then().statusCode(200).body("active", equalTo(true));
    }

    @Then("that customer cannot log in and is told the account is disabled")
    public void cannotLogIn() {
        auth.login(ctx.get("email"), TestData.PASSWORD).then().statusCode(403)
                .body("message", equalTo("This account is disabled. Please contact the store."));
    }

    @Then("that customer can log in again")
    public void canLogIn() {
        auth.login(ctx.get("email"), TestData.PASSWORD).then().statusCode(200);
    }

    // ---------------------------------------------------------------- coupons

    @Given("the admin has created a {string} coupon worth {string}")
    public void coupon(String type, String value) {
        String code = "BDD" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long id = admin.createCoupon(Fixtures.adminToken(), Map.of("code", code, "type", type, "value", new BigDecimal(value)))
                .then().statusCode(201).extract().<Integer>path("id");
        ctx.put("couponId", id);
        ctx.put("couponCode", code);
    }

    @When("the admin changes that coupon to {string} worth {string}")
    public void editCoupon(String type, String value) {
        long id = ctx.get("couponId");
        ctx.lastResponse(admin.updateCoupon(Fixtures.adminToken(), id, Map.of("type", type, "value", new BigDecimal(value))));
    }

    @Then("the coupon code is unchanged and its type is {string}")
    public void couponKept(String type) {
        ctx.lastResponse().then().body("code", equalTo(ctx.get("couponCode"))).body("type", equalTo(type));
    }

    // ---------------------------------------------------------------- reports

    @When("the admin opens the {string} report")
    public void report(String name) {
        ctx.lastResponse(admin.report(Fixtures.adminToken(), name, Map.of()));
    }

    @Then("the report is named {string} and has columns")
    public void reportShape(String name) {
        ctx.lastResponse().then().body("name", equalTo(name)).body("columns", not(empty()));
    }

    // ---------------------------------------------------------------- audit trail

    @When("the admin changes the price of that product to {string}")
    public void changePrice(String price) {
        long productId = ctx.get("productId");
        JsonPath p = products.get(productId).then().statusCode(200).extract().jsonPath();
        products.update(Fixtures.adminToken(), productId, ProductClient.body(p.getString("name"), p.getString("sku"),
                p.getString("category"), new BigDecimal(price), p.getInt("stock"))).then().statusCode(200);
    }

    @Then("the audit trail shows {string} changed from {string} to {string} by the admin")
    public void auditShows(String field, String from, String to) {
        long productId = ctx.get("productId");
        Response r = admin.audit(Fixtures.adminToken(), "PRODUCT", productId, null);
        r.then().statusCode(200)
                .body("[0].field", equalTo(field))
                .body("[0].oldValue", equalTo(from))
                .body("[0].newValue", equalTo(to))
                .body("[0].actor", equalTo(TestConfig.adminEmail()));
    }

    @Then("the audit trail integrity check passes")
    public void integrity() {
        admin.auditVerify(Fixtures.adminToken()).then().statusCode(200).body("valid", equalTo(true));
    }

    @When("the customer opens the audit trail")
    public void customerOpensAudit() {
        ctx.lastResponse(admin.audit(ctx.token(), null, null, null));
    }
}
