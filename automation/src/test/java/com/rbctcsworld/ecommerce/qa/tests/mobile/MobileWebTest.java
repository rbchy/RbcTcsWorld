package com.rbctcsworld.ecommerce.qa.tests.mobile;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.BasePage;
import com.rbctcsworld.ecommerce.qa.pages.CheckoutPage;
import com.rbctcsworld.ecommerce.qa.pages.HomePage;
import com.rbctcsworld.ecommerce.qa.pages.LoginPage;
import com.rbctcsworld.ecommerce.qa.pages.OrderPage;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The storefront in a real phone browser through Appium: Chrome on an Android emulator, Safari on an iOS
 * Simulator (or a real device). The same page objects as on the desktop - Appium speaks W3C WebDriver.
 *
 *   mvn -f automation/pom.xml test -Dgroups=mobile -DexcludedGroups=none -Dbrowser=android-chrome \
 *       -DuiUrl=http://10.0.2.2:5173 -DappiumUrl=http://127.0.0.1:4723
 * (10.0.2.2 is the computer itself, seen from the Android emulator; the iOS Simulator uses localhost.)
 * Excluded from every other run (pom.xml: excludedGroups=mobile). Guide: docs/modules/APPIUM_MOBILE_BN.md
 */
@Tag("mobile")
@Epic("Mobile web")
@Feature("Phone browsers (Appium)")
class MobileWebTest {

    private WebDriver driver;

    @RegisterExtension
    ScreenshotOnFailure screenshots = new ScreenshotOnFailure(() -> driver);

    @BeforeEach
    void start() {
        assumeTrue(DriverFactory.isMobile(), "phone tests need -Dbrowser=android-chrome or ios-safari and an Appium server");
        driver = DriverFactory.create();
    }

    @AfterEach
    void stop() {
        if (driver != null) driver.quit();
    }

    @Test
    @DisplayName("Phone: catalog fits the screen, pictures load, buttons are icons, search works")
    void catalogOnAPhone() {
        HomePage home = new HomePage(driver).open();
        ShellPage shell = new ShellPage(driver);
        HomePage.Picture mouse = home.pictureOf("ELEC-MOUSE-001");
        assertAll("phone layout",
                () -> assertTrue(shell.fitsTheScreen(), "no sideways scrolling"),
                () -> assertTrue(mouse.loaded(), "product picture decoded by the phone's browser"),
                () -> assertEquals("/products/elec-mouse-001.svg", mouse.src()),
                () -> assertTrue(shell.topBarButtonsAreIconsOnly(), "Exit shows only its icon"));

        home.search("mouse");
        assertTrue(home.productNames().stream().allMatch(n -> n.toLowerCase().contains("mouse")));
    }

    @Test
    @Tag("smoke")
    @DisplayName("Phone: new customer registers with the on-screen keyboard, buys and pays with the test card")
    void purchaseOnAPhone() {
        Fixtures.Product product = Fixtures.namedProduct("30.00", 10);
        String email = TestData.uniqueEmail();

        HomePage home = new LoginPage(driver).open().register(email, TestData.PASSWORD);
        assertTrue(home.isLoggedInAs(email), "header shows the new customer");

        home.search(product.sku()).addToCart(product.sku(), 2);
        assertTrue(home.flash().contains("2 item(s)"), home.flash());

        CheckoutPage checkout = home.goToCart().checkout();
        checkout.applyCoupon(TestData.COUPON_WELCOME10);
        assertEquals("$57.24", checkout.total());

        OrderPage order = checkout.placeOrder();
        order.pay(TestData.CARD_OK).waitForStatus("PAID");
        assertAll("paid on the phone",
                () -> assertTrue(order.notice().contains("Payment received")),
                () -> assertFalse(order.canPay()));
    }

    @Test
    @DisplayName("Phone: menu opens with the Menu button; Log out and Exit work from the menu")
    void menuLogoutAndExitOnAPhone() {
        Fixtures.Customer c = Fixtures.newCustomer();
        BasePage.loginWithToken(driver, c.token(), c.email());
        ShellPage shell = new ShellPage(driver).open("/");
        assertTrue(shell.waitLoggedInAs(c.email()));

        shell.openMenu().logoutFromMenu();
        assertTrue(shell.waitLoggedOut(), "logged out from the menu");

        shell.openMenu().exitFromMenu();
        assertAll(() -> assertTrue(shell.waitGoodbye()),
                  () -> assertTrue(shell.goodbyeSaysSignedOut()));
    }

    @Test
    @DisplayName("Phone: the device really is a phone (user agent) and the shop is reached at the configured address")
    void reallyAPhone() {
        new HomePage(driver).open();
        String ua = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("return navigator.userAgent;");
        String expected = TestConfig.browser().equalsIgnoreCase("ios-safari") ? "iPhone" : "Android";
        assertAll(() -> assertTrue(ua.contains(expected), ua),
                  () -> assertTrue(driver.getCurrentUrl().startsWith(TestConfig.uiUrl()), driver.getCurrentUrl()));
    }
}
