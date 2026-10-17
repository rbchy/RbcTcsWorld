package com.rbctcsworld.ecommerce.qa.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

/** Order detail page: pay, cancel, return, tracking timeline, payments (frontend/src/pages/OrderDetail.jsx). */
public class OrderPage extends BasePage {

    public OrderPage(WebDriver driver) {
        super(driver);
    }

    public OrderPage open(long orderId) {
        openRoute("/orders/" + orderId);
        driver.navigate().refresh();
        return waitLoaded();
    }

    public OrderPage waitLoaded() {
        visible("order-status");
        return this;
    }

    public String status() {
        return text("order-status");
    }

    /** "Ships to: Ada Lovelace, 12 Main St, ..." - the delivery address line. */
    public String shipsTo() {
        return text("order-ship-to");
    }

    public String orderNumber() {
        return text("order-number");
    }

    public String total() {
        return text("price-total");
    }

    /**
     * Pays with the given card. Waits until the page reacts: a new payment row (approved or declined
     * attempts are both stored), a new status or a changed message. Comparing a "snapshot" instead of
     * waiting for "an error is shown" keeps the wait correct when an error from an earlier try is still visible.
     */
    public OrderPage pay(String cardNumber) {
        String before = snapshot();
        type("card-number", cardNumber);
        type("card-cvv", "123");
        click("pay-button");
        wait.until(d -> !snapshot().equals(before));
        return this;
    }

    public OrderPage cancel() {
        String before = snapshot();
        click("cancel-order");
        wait.until(d -> !snapshot().equals(before));
        return this;
    }

    /** Waits until the order shows the expected status (the page reloads the order after every action). */
    public OrderPage waitForStatus(String expected) {
        wait.until(d -> expected.equals(textOrEmpty("order-status")));
        return this;
    }

    private String snapshot() {
        return textOrEmpty("order-status") + "|" + all(testId("payment-row")).size() + "|"
                + textOrEmpty("order-notice") + "|" + textOrEmpty("order-error");
    }

    public OrderPage requestReturn(String reason) {
        new Select(visible("return-reason")).selectByValue(reason);
        click("return-submit");
        visible("return-status");
        return this;
    }

    public String notice() { return text("order-notice"); }
    public String error() { return text("order-error"); }
    public String returnStatus() { return text("return-status"); }
    public String trackingNumber() { return text("tracking-number"); }
    public boolean canPay() { return isShown("pay-form"); }
    public boolean canCancel() { return isShown("cancel-order"); }

    public List<String> timeline() {
        visible("timeline-event");
        return attributeOfAll("timeline-event", "data-status");
    }

    public List<String> paymentStatuses() {
        return attributeOfAll("payment-row", "data-status");
    }

    public List<WebElement> items() {
        return all(testId("order-item"));
    }
}
