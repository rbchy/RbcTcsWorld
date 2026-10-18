package com.rbctcsworld.ecommerce.qa.stepdefinitions;

import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.context.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;

/** Steps for the faceted search (filter panel counts). */
public class FacetSteps {

    private final ScenarioContext ctx;
    private final ProductClient products = new ProductClient();

    public FacetSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("the shopper asks for the filter counts of {string}")
    public void counts(String q) {
        ctx.lastResponse(products.facets(Map.of("q", q)));
    }

    @When("the shopper asks for the filter counts of {string} in the colour {string}")
    public void countsInColour(String q, String colour) {
        ctx.lastResponse(products.facets(Map.of("q", q, "colors", colour)));
    }

    @When("the shopper asks for the filter counts with a minimum rating of {int}")
    public void badRating(int rating) {
        ctx.lastResponse(products.facets(Map.of("minRating", rating)));
    }

    @Then("the filter panel counts {int} item(s)")
    public void total(int n) {
        ctx.lastResponse().then().statusCode(200).body("total", equalTo(n));
    }

    @Then("the colour {string} is offered with {int} item(s)")
    public void colour(String colour, int n) {
        ctx.lastResponse().then().body("colors.find { it.value == '" + colour + "' }.count", equalTo(n));
    }
}
