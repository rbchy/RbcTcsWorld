package com.rbctcsworld.ecommerce.qa.stepdefinitions;

import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.context.ScenarioContext;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/** Steps for product variants (size / colour). Group names get a unique suffix per scenario. */
public class VariantSteps {

    private final ScenarioContext ctx;
    private final ProductClient products = new ProductClient();
    private final String suffix = "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

    public VariantSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    private static String orNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    @When("the shopper opens the grouped catalog for {string}")
    public void grouped(String q) {
        ctx.lastResponse(products.search(Map.of("q", q, "grouped", true)));
    }

    @Then("the catalog shows {int} card(s)")
    public void cards(int n) {
        ctx.lastResponse().then().statusCode(200).header("X-Total-Count", String.valueOf(n));
    }

    @Then("that card's item has {int} options in the colours {string} and the sizes {string}")
    public void options(int n, String colours, String sizes) {
        long card = ctx.lastResponse().then().extract().jsonPath().getLong("[0].id");
        String group = products.get(card).then().statusCode(200).extract().jsonPath().getString("variantGroup");
        products.variantGroups(group).then().statusCode(200)
                .body("[0].options", equalTo(n))
                .body("[0].colors", contains(Arrays.stream(colours.split(",\\s*")).toArray()))
                .body("[0].sizes", contains(Arrays.stream(sizes.split(",\\s*")).toArray()));
    }

    @Given("the store admin adds a {string} in group {string} with size {string} and colour {string}")   // Given or When
    public void addVariant(String name, String group, String size, String colour) {
        String g = group.isBlank() ? null : group + suffix;
        Response r = products.create(Fixtures.adminToken(), ProductClient.variantBody(name, "V" + UUID.randomUUID().toString().substring(0, 10),
                new BigDecimal("12.00"), 10, g, orNull(size), orNull(colour)));
        ctx.lastResponse(r);
        if (r.statusCode() == 201) ctx.put("productId", r.jsonPath().getLong("id"));
    }

    @When("the customer orders {int} of that variant")
    public void order(int qty) {
        long productId = ctx.get("productId");
        new CartClient().add(ctx.token(), productId, qty).then().statusCode(201);
        Response r = new OrderClient().place(ctx.token());
        r.then().statusCode(201);
        ctx.lastResponse(r);
    }

    @Then("the order line is named {string}")
    public void lineName(String name) {
        List<String> names = ctx.lastResponse().then().extract().jsonPath().getList("items.name");
        org.junit.jupiter.api.Assertions.assertEquals(List.of(name), names);
    }
}
