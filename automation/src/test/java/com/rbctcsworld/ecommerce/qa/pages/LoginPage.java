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
        click("auth-submit");
    }

    /** Client-side validation message under a field ("email", "password", "confirm"), or "" if none. */
    public String fieldError(String field) {
        return textOrEmpty(field + "-error");
    }

    /** Registers and waits until the header shows the new user. */
    public HomePage register(String email, String password) {
        switchToRegister();
        submit(email, password);
        wait.until(d -> isShown("nav-user"));
        return new HomePage(driver);
    }

    public String error() {
        return text("auth-error");
    }

    public boolean isDisplayed() {
        return isShown("auth-title");
    }
}
