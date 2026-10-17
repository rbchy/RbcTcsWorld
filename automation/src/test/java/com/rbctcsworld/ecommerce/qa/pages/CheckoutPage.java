package com.rbctcsworld.ecommerce.qa.pages;

import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.Map;

/** Checkout page: price breakdown, coupon, delivery address, place order (frontend/src/pages/Checkout.jsx). */
public class CheckoutPage extends BasePage {

    public CheckoutPage(WebDriver driver) {
        super(driver);
    }

    /** Opens /checkout directly (the customer must be logged in and have something in the cart). */
    public CheckoutPage open() {
        openRoute("/checkout");
        return waitLoaded();
    }

    CheckoutPage waitLoaded() {
        visible("price-total");
        visible("addr-fullName");
        return this;
    }

    public CheckoutPage applyCoupon(String code) {
        String before = total();
        type("coupon-input", code);
        click("coupon-apply");
        // wait for either a new price or an error message
        wait.until(d -> isShown("checkout-error") || !total().equals(before));
        return this;
    }

    public String subtotal() { return text("price-subtotal"); }
    public String discount() { return text("price-discount"); }
    public String shipping() { return text("price-shipping"); }
    public String tax() { return text("price-tax"); }
    public String total() { return text("price-total"); }

    public String error() {
        return text("checkout-error");
    }

    /** Fills every address field (keys as in TestData.ADDRESS); a missing key leaves that field empty. */
    public CheckoutPage enterAddress(Map<String, String> a) {
        for (String f : new String[]{"fullName", "line1", "line2", "city", "zip", "phone"}) {
            type("addr-" + f, a.getOrDefault(f, ""));
        }
        chooseState(a.getOrDefault("state", ""));
        return this;
    }

    private void chooseState(String code) {
        WebElement select = visible("addr-state");
        if (DriverFactory.isMobile()) {
            // a phone opens its own picker wheel for <select>; set the value like the picker does instead
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                    select, code);
        } else {
            new Select(select).selectByValue(code);
        }
        wait.until(d -> code.equals(d.findElement(testId("addr-state")).getDomProperty("value")));
    }

    /** What a field currently contains (e.g. filled in from the last order). */
    public String addressField(String field) {
        return visible("addr-" + field).getDomProperty("value");
    }

    /** The message under a field, or "" when there is none. */
    public String fieldError(String field) {
        return textOrEmpty("addr-" + field + "-error");
    }

    /** Places the order to TestData.ADDRESS - the normal case for journeys that are not about the address. */
    public OrderPage placeOrder() {
        return placeOrderTo(TestData.ADDRESS);
    }

    public OrderPage placeOrderTo(Map<String, String> address) {
        enterAddress(address);
        click("place-order");
        return new OrderPage(driver).waitLoaded();
    }

    /** Clicks "Place order" and waits for the field messages; the page stays on checkout. */
    public CheckoutPage placeOrderExpectingErrors() {
        click("place-order");
        wait.until(d -> !all(testId("addr-fullName-error")).isEmpty() || !all(testId("addr-zip-error")).isEmpty()
                || !all(testId("addr-state-error")).isEmpty() || isShown("checkout-error"));
        return this;
    }
}
