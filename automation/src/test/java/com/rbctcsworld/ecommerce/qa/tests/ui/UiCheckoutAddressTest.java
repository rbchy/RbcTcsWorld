package com.rbctcsworld.ecommerce.qa.tests.ui;

import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.BasePage;
import com.rbctcsworld.ecommerce.qa.pages.CheckoutPage;
import com.rbctcsworld.ecommerce.qa.pages.OrderPage;
import com.rbctcsworld.ecommerce.qa.reporting.ScreenshotOnFailure;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.WebDriver;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The delivery address form on the checkout page (FR-CHK-07..10). */
@Tag("ui")
@Epic("Storefront UI")
@Feature("Delivery address")
class UiCheckoutAddressTest {

    private final CartClient cart = new CartClient();
    private WebDriver driver;
    private Customer customer;

    @RegisterExtension
    ScreenshotOnFailure screenshots = new ScreenshotOnFailure(() -> driver);

    @BeforeEach
    void customerWithACart() {
        driver = DriverFactory.create();
        customer = Fixtures.newCustomer();
        cart.add(customer.token(), Fixtures.product("12.00", 5), 1).then().statusCode(201);
        BasePage.loginWithToken(driver, customer.token(), customer.email());
    }

    @AfterEach
    void stop() {
        if (driver != null) driver.quit();
    }

    @Test
    @DisplayName("Empty address: every required field says so, and no order is placed")
    void requiredFieldsAreNamed() {
        CheckoutPage checkout = new CheckoutPage(driver).open().placeOrderExpectingErrors();

        assertAll(
                () -> assertEquals("Please fill in this field", checkout.fieldError("fullName")),
                () -> assertEquals("Please fill in this field", checkout.fieldError("line1")),
                () -> assertEquals("Please fill in this field", checkout.fieldError("city")),
                () -> assertEquals("Please fill in this field", checkout.fieldError("state")),
                () -> assertEquals("Please fill in this field", checkout.fieldError("zip")),
                () -> assertEquals("", checkout.fieldError("line2"), "optional"),
                () -> assertEquals("", checkout.fieldError("phone"), "optional"));
        assertTrue(driver.getCurrentUrl().contains("#/checkout"), "still on checkout: " + driver.getCurrentUrl());
    }

    @Test
    @DisplayName("A wrong ZIP code: the shop's message appears under the ZIP field")
    void serverMessageUnderTheField() {
        Map<String, String> wrongZip = new HashMap<>(TestData.ADDRESS);
        wrongZip.put("zip", "ABCDE");
        CheckoutPage checkout = new CheckoutPage(driver).open();
        checkout.enterAddress(wrongZip).placeOrderExpectingErrors();

        assertEquals("must be a US ZIP code: 12345 or 12345-6789", checkout.fieldError("zip"));
        assertEquals("", checkout.fieldError("fullName"));
    }

    @Test
    @Tag("smoke")
    @DisplayName("The order page says where it ships, and the next checkout fills in the same address")
    void addressOnTheOrderAndRememberedForNextTime() {
        OrderPage order = new CheckoutPage(driver).open().placeOrder();
        assertEquals("Ships to: Ada Lovelace, 12 Main St, Apt 4, Pittsburgh, PA 15213", order.shipsTo());

        cart.add(customer.token(), Fixtures.product("8.00", 5), 1).then().statusCode(201);
        CheckoutPage next = new CheckoutPage(driver).open();
        new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(10))
                .until(d -> "Ada Lovelace".equals(next.addressField("fullName")));
        assertAll("filled in from the last order",
                () -> assertEquals("12 Main St", next.addressField("line1")),
                () -> assertEquals("Apt 4", next.addressField("line2")),
                () -> assertEquals("Pittsburgh", next.addressField("city")),
                () -> assertEquals("PA", next.addressField("state")),
                () -> assertEquals("15213", next.addressField("zip")),
                () -> assertEquals("412-555-0100", next.addressField("phone")));
    }
}
