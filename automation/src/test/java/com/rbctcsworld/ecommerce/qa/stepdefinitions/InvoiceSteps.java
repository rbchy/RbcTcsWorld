package com.rbctcsworld.ecommerce.qa.stepdefinitions;

import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.context.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for the invoice PDF. */
public class InvoiceSteps {

    private final ScenarioContext ctx;
    private final OrderClient orders = new OrderClient();

    public InvoiceSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("the customer downloads the invoice of the order")
    public void download() {
        long orderId = ctx.get("orderId");
        ctx.lastResponse(orders.invoice(ctx.token(), orderId));
    }

    @Then("the response is a PDF invoice with the total {string}")
    public void pdfWithTotal(String total) {
        ctx.lastResponse().then().contentType("application/pdf");
        String pdf = new String(ctx.lastResponse().asByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4"), "a PDF file");
        assertTrue(pdf.contains("(" + total + ")"), "the total is printed");
    }
}
