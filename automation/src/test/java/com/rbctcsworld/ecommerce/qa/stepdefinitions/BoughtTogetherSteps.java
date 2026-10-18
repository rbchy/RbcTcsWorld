package com.rbctcsworld.ecommerce.qa.stepdefinitions;

import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.context.ScenarioContext;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

/** Steps for "customers who bought this also bought". */
public class BoughtTogetherSteps {

    private final ScenarioContext ctx;
    private final ProductClient products = new ProductClient();
    private final CartClient cart = new CartClient();
    private final OrderClient orders = new OrderClient();

    public BoughtTogetherSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("another product priced {string} with stock {int} exists")
    public void anotherProduct(String price, int stock) {
        ctx.put("secondProductId", Fixtures.product(price, stock));
    }

    @Given("{int} customers each bought both products in one order")
    public void bothBought(int customers) {
        long first = ctx.get("productId");
        long second = ctx.get("secondProductId");
        for (int i = 0; i < customers; i++) {
            Customer c = Fixtures.newCustomer();
            cart.add(c.token(), first, 1).then().statusCode(201);
            cart.add(c.token(), second, 1).then().statusCode(201);
            orders.place(c.token()).then().statusCode(201);
        }
    }

    @When("the shopper asks what was bought together with the first product")
    public void ask() {
        long first = ctx.get("productId");
        ctx.lastResponse(products.boughtTogether(first, null));
    }

    @When("the shopper asks for {int} recommendations for the first product")
    public void askFor(int limit) {
        long first = ctx.get("productId");
        ctx.lastResponse(products.boughtTogether(first, limit));
    }

    @Then("the first recommendation is the second product")
    public void firstIsSecond() {
        long second = ctx.get("secondProductId");
        ctx.lastResponse().then().body("[0].id", equalTo((int) second));
    }

    @Then("there are no recommendations")
    public void none() {
        ctx.lastResponse().then().body("$", empty());
    }
}
