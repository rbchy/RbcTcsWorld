package com.rbctcsworld.ecommerce.qa.tests.ui;

import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.database.DatabaseUtils;
import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.BasePage;
import com.rbctcsworld.ecommerce.qa.pages.LoginPage;
import com.rbctcsworld.ecommerce.qa.pages.OrdersPage;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("ui")
@Epic("Storefront UI")
@Feature("Login")
class UiAuthTest {

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
    @DisplayName("Wrong password shows an error and keeps the user on the login page")
    void wrongPassword() {
        Fixtures.Customer c = Fixtures.newCustomer();
        LoginPage login = new LoginPage(driver).open();
        login.submit(c.email(), "Wrong-Password1!");
        assertFalse(login.error().isBlank(), "error message shown");
        assertTrue(login.isDisplayed());
    }

    @Test
    @DisplayName("Protected page asks for login, then returns to that page")
    void protectedRouteRedirect() {
        Fixtures.Customer c = Fixtures.newCustomer();
        LoginPage login = new LoginPage(driver).openProtected("/orders");
        assertTrue(login.isDisplayed(), "anonymous user sees the login form");

        login.submit(c.email(), TestData.PASSWORD);
        OrdersPage orders = new OrdersPage(driver).waitLoaded();
        assertTrue(orders.isEmpty(), "back on My orders, which is empty for a new customer");
    }

    @Test
    @DisplayName("Expired access token: the storefront refreshes the session silently and the page still loads")
    void silentRefresh() {
        String email = TestData.uniqueEmail();
        String refresh = new AuthClient().register(email, TestData.PASSWORD).then().statusCode(201)
                .extract().path("refreshToken");
        // the access token is no longer valid (expired after 15 minutes, simulated with a dead token)
        BasePage.loginWithTokens(driver, "expired.access.token", refresh, email, "CUSTOMER");

        OrdersPage orders = new OrdersPage(driver).open().waitLoaded();

        assertAll(
                () -> assertTrue(orders.isEmpty(), "My orders loaded instead of the login form"),
                () -> assertTrue(orders.stored("token").startsWith("eyJ"), "a real JWT replaced the dead token"),
                () -> assertNotEquals(refresh, orders.stored("refreshToken"), "refresh token rotated"));
    }

    @Test
    @DisplayName("Log out ends the session on the server: the refresh token is revoked in the database")
    void logoutRevokesOnTheServer() {
        assumeTrue(DatabaseUtils.isAvailable(), "database not reachable from the tests");
        String email = TestData.uniqueEmail();
        var session = new AuthClient().register(email, TestData.PASSWORD).then().statusCode(201).extract();
        String refresh = session.path("refreshToken");
        BasePage.loginWithTokens(driver, session.path("token"), refresh, email, "CUSTOMER");
        ShellPage shell = new ShellPage(driver).open("/");
        assertTrue(shell.waitLoggedInAs(email));

        shell.logout();

        assertAll(
                () -> assertTrue(shell.waitLoggedOut(), "top bar offers Log in again"),
                () -> assertNull(shell.stored("refreshToken"), "browser forgot the refresh token"),
                () -> assertTrue(revokedInDatabase(refresh), "server revoked it (checked in the database, not by using it)"));
    }

    @Test
    @DisplayName("Exit logs out, revokes the session on the server and shows the goodbye page")
    void exitEndsTheSessionAndSaysGoodbye() throws Exception {
        String email = TestData.uniqueEmail();
        var session = new AuthClient().register(email, TestData.PASSWORD).then().statusCode(201).extract();
        String refresh = session.path("refreshToken");
        BasePage.loginWithTokens(driver, session.path("token"), refresh, email, "CUSTOMER");
        ShellPage shell = new ShellPage(driver).open("/");
        assertTrue(shell.waitLoggedInAs(email));

        shell.exit();

        assertAll(
                () -> assertTrue(shell.waitGoodbye(), "goodbye page (a test browser window is never closed by a page)"),
                () -> assertTrue(shell.goodbyeSaysSignedOut()),
                () -> assertNull(shell.stored("token"), "browser forgot the session"),
                () -> assertNull(shell.stored("refreshToken")),
                () -> assertTrue(shell.waitLoggedOut(), "top bar offers Log in again"));
        if (DatabaseUtils.isAvailable()) assertTrue(revokedInDatabase(refresh), "server revoked the refresh token");

        shell.backToShop();
        assertTrue(shell.waitLoggedOut(), "back in the shop as a guest");
    }

    @Test
    @DisplayName("Log out and Exit are also in the menu's Account section; Exit works for guests too")
    void menuLogoutAndExit() {
        Fixtures.Customer c = Fixtures.newCustomer();
        BasePage.loginWithToken(driver, c.token(), c.email());
        ShellPage shell = new ShellPage(driver).open("/");
        assertTrue(shell.waitLoggedInAs(c.email()));

        shell.logoutFromMenu();
        assertTrue(shell.waitLoggedOut());

        shell.exitFromMenu();
        assertAll(() -> assertTrue(shell.waitGoodbye()),
                  () -> assertTrue(shell.goodbyeSaysSignedOut()));
    }

    /** The logout call runs in the background; poll the row instead of sleeping a fixed time. */
    private static boolean revokedInDatabase(String refresh) throws Exception {
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(refresh.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for (int i = 0; i < 20; i++) {
            Object n = DatabaseUtils.scalar(
                    "select count(*) from refresh_tokens where token_hash = ? and revoked_at is not null", hash);
            if (((Number) n).intValue() == 1) return true;
            Thread.sleep(250);
        }
        return false;
    }
}
