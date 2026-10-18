package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order invoice as a PDF file: REQ-ORD-11. */
@Tag("api")
@Tag("orders")
@Epic("Orders")
@Feature("Invoice PDF")
class InvoiceApiTest {

    private final OrderClient orders = new OrderClient();

    @Test
    @Tag("smoke")
    @DisplayName("A paid order has an invoice: a real PDF, named after the order, with the items and the total")
    void paidOrderInvoice() {
        var product = Fixtures.namedProduct("12.50", 5);
        Customer c = Fixtures.newCustomer();
        long id = Fixtures.paidOrder(c, product.id(), 2);
        String number = orders.tracking(c.token(), id).then().statusCode(200).extract().path("orderNumber");

        Response r = orders.invoice(c.token(), id);
        r.then().statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", containsString("invoice-" + number + ".pdf"))
                .header("Cache-Control", containsString("no-store"));
        String pdf = new String(r.asByteArray(), StandardCharsets.ISO_8859_1);
        org.hamcrest.MatcherAssert.assertThat(pdf, startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("(Order " + number + ")"), "the order number is on the invoice");
        assertTrue(pdf.contains("(" + product.name() + ")"), "the item is on the invoice");
        assertTrue(pdf.contains("($25.00)"), "the line total is on the invoice");
        assertTrue(pdf.contains("($32.49)"), "the order total (25.00 + 5.99 shipping + 1.50 tax) is on the invoice");
        assertTrue(pdf.contains("(Status: PAID"), "an invoice of a paid order says paid");
    }

    @Test
    @DisplayName("An order that is not paid yet says payment due")
    void unpaidInvoice() {
        var product = Fixtures.namedProduct("10.00", 5);
        Customer c = Fixtures.newCustomer();
        long id = Fixtures.placedOrder(c, product.id(), 1);
        String pdf = new String(orders.invoice(c.token(), id).then().statusCode(200).extract().asByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.contains("payment due"));
    }

    @Test
    @DisplayName("Only the customer of the order gets it: 404 for another customer, 401 without a login")
    void privacy() {
        var product = Fixtures.namedProduct("10.00", 5);
        Customer owner = Fixtures.newCustomer();
        long id = Fixtures.placedOrder(owner, product.id(), 1);
        orders.invoice(Fixtures.newCustomer().token(), id).then().statusCode(404);
        orders.invoice(null, id).then().statusCode(401);
        orders.invoice(owner.token(), 999_999_999L).then().statusCode(404);
    }
}
