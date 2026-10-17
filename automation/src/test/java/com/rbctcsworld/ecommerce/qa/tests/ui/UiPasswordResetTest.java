package com.rbctcsworld.ecommerce.qa.tests.ui;

import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.MailpitClient;
import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.HomePage;
import com.rbctcsworld.ecommerce.qa.pages.LoginPage;
import com.rbctcsworld.ecommerce.qa.pages.PasswordResetPage;
import com.rbctcsworld.ecommerce.qa.reporting.ScreenshotOnFailure;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** "Forgot your password?" in the browser, with the link read from the real e-mail in Mailpit. */
@Tag("ui")
@Epic("Storefront UI")
@Feature("Password reset")
class UiPasswordResetTest {

    private static final MailpitClient MAILPIT = new MailpitClient();
    private final AuthClient auth = new AuthClient();
    private WebDriver driver;

    @RegisterExtension
    ScreenshotOnFailure screenshots = new ScreenshotOnFailure(() -> driver);

    @BeforeAll
    static void mailServer() {
        assumeTrue(MAILPIT.isAvailable(), "Mailpit is not running (-DmailpitUrl)");
    }

    @BeforeEach
    void start() {
        driver = DriverFactory.create();
    }

    @AfterEach
    void stop() {
        if (driver != null) driver.quit();
    }

    @Test
    @Tag("smoke")
    @DisplayName("Forgot password -> e-mail link -> new password -> log in with it")
    void resetFromTheLoginPage() {
        String email = TestData.uniqueEmail();
        auth.register(email, TestData.PASSWORD).then().statusCode(201);

        PasswordResetPage forgot = new LoginPage(driver).open().forgotPassword();
        assertTrue(forgot.requestLink(email).contains("If an account exists for this e-mail"));

        String token = MAILPIT.resetToken(email, 1);
        PasswordResetPage reset = new PasswordResetPage(driver).openLink(token);
        assertTrue(reset.setNewPassword("BrandNew1!", "BrandNew1!").contains("Your password was changed"));

        HomePage home = new LoginPage(driver).open().login(email, "BrandNew1!");
        assertTrue(home.isLoggedInAs(email));
    }

    @Test
    @DisplayName("Mismatched passwords are caught on the page; a used link shows the shop's message")
    void mistakesOnTheResetPage() {
        String email = TestData.uniqueEmail();
        auth.register(email, TestData.PASSWORD).then().statusCode(201);
        auth.forgotPassword(email).then().statusCode(202);
        String token = MAILPIT.resetToken(email, 1);

        PasswordResetPage reset = new PasswordResetPage(driver).openLink(token);
        assertEquals("The two passwords are different.", reset.mismatch("BrandNew1!", "BrandNew2!"));

        auth.resetPassword(token, "UsedElsewhere1!").then().statusCode(204);    // the link is spent now
        assertEquals("This reset link is invalid or has expired. Please ask for a new one.",
                reset.setNewPasswordExpectingError("BrandNew1!"));
        assertTrue(driver.getCurrentUrl().startsWith(TestConfig.uiUrl()));
    }
}
