package com.rbctcsworld.ecommerce.qa.pages;

import org.openqa.selenium.WebDriver;

/** "Forgot password" and "Choose a new password" pages (frontend/src/pages/PasswordReset.jsx). */
public class PasswordResetPage extends BasePage {

    public PasswordResetPage(WebDriver driver) {
        super(driver);
    }

    PasswordResetPage waitForgotForm() {
        visible("forgot-email");
        return this;
    }

    /** Asks for a link and returns the confirmation text (the same for every e-mail). */
    public String requestLink(String email) {
        type("forgot-email", email);
        click("forgot-submit");
        return text("forgot-sent");
    }

    /** Opens the link from the e-mail, as the customer's browser would. */
    public PasswordResetPage openLink(String token) {
        openRoute("/reset-password?token=" + token);
        visible("reset-password");
        return this;
    }

    /** Sets the new password and returns the success message. */
    public String setNewPassword(String password, String repeated) {
        type("reset-password", password);
        type("reset-confirm", repeated);
        click("reset-submit");
        return text("reset-done");
    }

    /** Different passwords: the page says so before anything is sent. */
    public String mismatch(String password, String repeated) {
        type("reset-password", password);
        type("reset-confirm", repeated);
        click("reset-submit");
        return text("reset-confirm-error");
    }

    /** Submits a valid new password and returns the shop's error (e.g. a used link). */
    public String setNewPasswordExpectingError(String password) {
        type("reset-password", password);
        type("reset-confirm", password);
        click("reset-submit");
        return text("reset-error");
    }
}
