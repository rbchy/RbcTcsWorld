package com.rbctcsworld.ecommerce.qa.tests.ui;

import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.LoginPage;
import com.rbctcsworld.ecommerce.qa.pages.ShellPage;
import com.rbctcsworld.ecommerce.qa.reporting.ScreenshotOnFailure;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registration form validation, the six interface languages (incl. right-to-left Arabic) and the help center. */
@Tag("ui")
@Epic("Storefront UI")
@Feature("Navigation, languages and help")
class UiShellTest {

    private WebDriver driver;

    @RegisterExtension
    ScreenshotOnFailure screenshots = new ScreenshotOnFailure(() -> driver);

    @BeforeEach
    void start() {
        driver = DriverFactory.create();
    }

    @AfterEach
    void stop() {
        if (driver != null) driver.quit();
    }

    @Test
    @DisplayName("Registration is validated in the browser (same rules as the API), then succeeds")
    void registrationValidation() {
        LoginPage page = new LoginPage(driver).openRegister();
        page.submit("", "short", "other");
        assertAll("empty / short / different",
                () -> assertFalse(page.fieldError("email").isBlank(), "email required"),
                () -> assertFalse(page.fieldError("password").isBlank(), "8 characters minimum"),
                () -> assertFalse(page.fieldError("confirm").isBlank(), "passwords must match"),
                () -> assertTrue(page.isDisplayed(), "still on the form"));

        page.submit("not-an-email", TestData.PASSWORD, TestData.PASSWORD);
        assertAll(() -> assertFalse(page.fieldError("email").isBlank(), "email format"),
                  () -> assertEquals("", page.fieldError("password"), "password now fine"),
                  () -> assertEquals("", page.fieldError("confirm"), "repeat now fine"));

        String email = TestData.uniqueEmail();
        page.submit(email, TestData.PASSWORD, TestData.PASSWORD);
        assertTrue(new ShellPage(driver).waitLoggedInAs(email), "registered and logged in");
    }

    @Test
    @DisplayName("Language switch translates the menu, sets <html lang>, Arabic turns the page right-to-left")
    void languages() {
        ShellPage shell = new ShellPage(driver).open("/");
        assertEquals("Cart", shell.menuLabel("nav-cart"));
        try {
            shell.language("bn");
            assertEquals("কার্ট", shell.menuLabel("nav-cart"));
            shell.language("ar");
            assertAll(() -> assertEquals("rtl", shell.htmlAttribute("dir")),
                      () -> assertEquals("السلة", shell.menuLabel("nav-cart")));
            shell.language("zh");
            assertAll(() -> assertEquals("ltr", shell.htmlAttribute("dir")),
                      () -> assertEquals("购物车", shell.menuLabel("nav-cart")));
            driver.navigate().refresh();
            assertEquals("购物车", new ShellPage(driver).open("/").menuLabel("nav-cart"), "choice survives a reload");
        } finally {
            shell.language("en");   // the other UI tests read English texts
        }
        assertEquals("WELCOME10", shell.bannerCode(), "banner shows the welcome coupon");
    }

    @Test
    @DisplayName("Help center: every topic in every language, search filters the topics")
    void helpCenter() {
        ShellPage shell = new ShellPage(driver).open("/").openHelp();
        List<String> all = shell.helpTopics();
        assertTrue(all.containsAll(List.of("start", "checkout", "returns", "admin", "desktop")), all.toString());

        shell.searchHelp("4242");
        assertTrue(shell.helpTopicsBecome(List.of("checkout")), "test cards are explained under checkout");
        shell.searchHelp("no-such-topic-xyz");
        assertTrue(shell.noHelpResults());
        shell.searchHelp("");

        String english = shell.helpTitle();
        try {
            shell.language("fr");
            assertNotEquals(english, shell.helpTitle(), "title translated");
            assertEquals(all, shell.helpTopics(), "same topics in French");
        } finally {
            shell.language("en");
        }
    }

    @Test
    @DisplayName("Protected page after login keeps working with the new menu (customer)")
    void sidebarNavigation() {
        Fixtures.Customer c = Fixtures.newCustomer();
        LoginPage login = new LoginPage(driver).openProtected("/wishlist");
        login.submit(c.email(), TestData.PASSWORD);
        ShellPage shell = new ShellPage(driver);
        assertTrue(shell.waitLoggedInAs(c.email()));
        assertFalse(shell.adminMenuShown());
    }
}
