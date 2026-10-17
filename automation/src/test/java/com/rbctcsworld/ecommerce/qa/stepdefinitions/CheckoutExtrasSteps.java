package com.rbctcsworld.ecommerce.qa.stepdefinitions;

import com.rbctcsworld.ecommerce.qa.api.AddressClient;
import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.context.ScenarioContext;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Steps for guest checkout and the address book. */
public class CheckoutExtrasSteps {

    private final ScenarioContext ctx;
    private final AuthClient auth = new AuthClient();
    private final AddressClient addresses = new AddressClient();

    public CheckoutExtrasSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("a guest starts a checkout with a new e-mail")
    public void guest() {
        String email = TestData.uniqueEmail();
        ctx.put("guestEmail", email);
        Response r = auth.guest(email);
        r.then().statusCode(201);
        ctx.token(r.path("token"));
    }

    @When("a guest starts a checkout with the same e-mail again")
    public void guestAgain() {
        ctx.lastResponse(auth.guest(ctx.<String>get("guestEmail")));
    }

    @When("the guest orders {int} of a new product and pays")
    public void orderAndPay(int qty) {
        long product = Fixtures.product("10.00", 5);
        new CartClient().add(ctx.token(), product, qty).then().statusCode(201);
        long order = new OrderClient().place(ctx.token()).then().statusCode(201).extract().jsonPath().getLong("id");
        ctx.lastResponse(new OrderClient().pay(ctx.token(), order, "4242424242424242"));
    }

    @Given("the customer saves the address {string} as {string}")   // Given or When
    public void save(String line1, String label) {
        ctx.lastResponse(addresses.add(ctx.token(), AddressClient.body(label, line1, false)));
    }

    @When("the customer makes {string} the default")
    public void makeDefault(String label) {
        List<Map<String, Object>> all = addresses.list(ctx.token()).then().statusCode(200).extract().jsonPath().getList("$");
        long id = all.stream().filter(a -> label.equals(a.get("label"))).map(a -> ((Number) a.get("id")).longValue()).findFirst().orElseThrow();
        addresses.makeDefault(ctx.token(), id).then().statusCode(200);
    }

    @Then("the default address is {string}")
    public void theDefault(String label) {
        List<String> defaults = addresses.list(ctx.token()).then().statusCode(200).extract().jsonPath().getList("findAll { it.isDefault }.label");
        assertEquals(List.of(label), defaults);
    }
}
