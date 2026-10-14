package com.rbctcsworld.ecommerce.qa.pages;

import org.openqa.selenium.WebDriver;

/** Login / registration page (frontend/src/pages/Login.jsx). */
public class LoginPage extends BasePage {

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public LoginPage open() {
        openRoute("/login");
        visible("auth-title");
        return this;
    }

    /** Opens a page that needs a session (e.g. "/orders") while logged out: the app shows this login form. */
    public LoginPage openProtected(String route) {
        openRoute(route);
        visible("auth-title");
        return this;
    }

    /** data-mode (login | register) instead of the heading text: the page works in six languages. */
    public String mode() {
        return wait.until(d -> d.findElement(testId("auth-card")).getDomAttribute("data-mode"));
    }

    public LoginPage switchToRegister() {
        if ("login".equals(mode())) click("auth-toggle");
        wait.until(d -> "register".equals(mode()));
        return this;
    }

    public LoginPage openRegister() {
        openRoute("/register");
        wait.until(d -> "register".equals(mode()));
        return this;
    }

    /** Fills the form; in register mode the repeated password gets the same value. */
    public void submit(String email, String password) {
        submit(email, password, password);
    }

    public void submit(String email, String password, String repeated) {
        type("email-input", email);
        type("password-input", password);
        if ("register".equals(mode())) type("confirm-input", repeated);
        if (com.rbctcsworld.ecommerce.qa.drivers.DriverFactory.isMobile()) {
            // close the on-screen keyboard first: while it slides away the page moves, and a tap at the button's
            // old position missed it on the Android emulator (nothing happened)
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "if (document.activeElement) document.activeElement.blur();"
                            + "document.querySelector('[data-testid=auth-submit]').scrollIntoView({block: 'center'});");
        }
        click("auth-submit");
    }

    private java.util.List<String> fieldErrors() {
        return java.util.stream.Stream.of("email", "password", "confirm").map(this::fieldError)
                .filter(e -> !e.isBlank()).toList();
    }

    /** Client-side validation message under a field ("email", "password", "confirm"), or "" if none. */
    public String fieldError(String field) {
        return textOrEmpty(field + "-error");
    }

    /** Registers and waits until the header shows the new user. */
    public HomePage register(String email, String password) {
        switchToRegister();
        submit(email, password);
        // logged in - or a message that says why not (a dropped key on a slow phone shows up here, readable)
        try {
            wait.until(d -> isShown("nav-user") || isShown("auth-error") || !fieldErrors().isEmpty());
        } catch (org.openqa.selenium.TimeoutException e) {
            throw new AssertionError("registration: no reaction to \"Create account\" (still " + mode() + " form, typed e-mail: "
                    + driver.findElement(testId("email-input")).getDomProperty("value") + ")", e);
        }
        if (!isShown("nav-user")) {
            throw new AssertionError("registration did not log in: " + textOrEmpty("auth-error") + " " + fieldErrors()
                    + " (typed e-mail: " + driver.findElement(testId("email-input")).getDomProperty("value") + ")");
        }
        return new HomePage(driver);
    }

    public String error() {
        return text("auth-error");
    }

    public boolean isDisplayed() {
        return isShown("auth-title");
    }
}
