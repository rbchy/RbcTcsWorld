package com.rbctcsworld.ecommerce.qa.tests.ui;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.WishlistClient;
import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.BasePage;
import com.rbctcsworld.ecommerce.qa.reporting.ScreenshotOnFailure;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Accessibility (WCAG 2.1 level A and AA) of every storefront page, checked with Deque axe-core inside the real
 * browser. A "serious" or "critical" violation fails the test (e.g. a form field without a label, text with too
 * little contrast, a page without a language); "moderate" and "minor" ones are reported in Allure only.
 * Automated rules find roughly a third to a half of accessibility problems - keyboard and screen-reader checks
 * by a person are still needed (see docs/modules/API_CONTRACT_A11Y_BN.md).
 */
@Tag("ui")
@Tag("a11y")
@Epic("Storefront UI")
@Feature("Accessibility (WCAG 2.1 AA)")
class AccessibilityTest {

    private static final List<String> WCAG_21_AA = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");
    private static final List<String> BLOCKING = List.of("serious", "critical");

    private static Customer me;
    private static Fixtures.Product product;
    private static long unpaidOrder;

    private WebDriver driver;

    @RegisterExtension
    ScreenshotOnFailure screenshots = new ScreenshotOnFailure(() -> driver);

    @BeforeAll
    static void data() {
        product = Fixtures.namedProduct("19.99", 50);
        me = Fixtures.verifiedBuyer(product.id());                 // may write a review on the product page
        unpaidOrder = Fixtures.placedOrder(me, product.id(), 1);   // order page shows the payment form
        new CartClient().add(me.token(), product.id(), 1).then().statusCode(201);
        new WishlistClient().add(me.token(), product.id()).then().statusCode(201);
    }

    @BeforeEach
    void start() {
        driver = DriverFactory.create();
    }

    @AfterEach
    void stop() {
        if (driver != null) driver.quit();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Login,           /login,           none,     auth-submit",
            "Registration,    /register,        none,     confirm-input",
            "Forgot password, /forgot-password, none,     forgot-email",
            "New password,    /reset-password?token=t, none, reset-password",
            "Help center,     /help,            none,     help-topic",
            "Goodbye (Exit),  /goodbye,         none,     goodbye",
            "Catalog,         /,                customer, product-card",
            "Product detail,  /products/{p},    customer, detail-name",
            "Wishlist,        /wishlist,        customer, wishlist-table",
            "Cart,            /cart,            customer, cart-table",
            "Checkout,        /checkout,        customer, place-order",
            "Order history,   /orders,          customer, orders-table",
            "Order and pay,   /orders/{o},      customer, order-status",
            "Admin dashboard, /admin,           admin,    stat-orders",
            "Admin products,  /admin/products,  admin,    admin-product-table",
            "Admin orders,    /admin/orders,    admin,    admin-order-table",
            "Admin e-mails,   /admin/notifications, admin, admin-notification-table",
            "Admin audit,     /admin/audit,     admin,    audit-table"})
    @DisplayName("Page meets WCAG 2.1 AA (no serious or critical axe violations)")
    void wcag21aa(String page, String route, String session, String readyTestId) {
        if ("customer".equals(session)) BasePage.loginWithToken(driver, me.token(), me.email());
        if ("admin".equals(session)) BasePage.loginWithToken(driver, Fixtures.adminToken(), TestConfig.adminEmail(), "ADMIN");
        driver.get(TestConfig.uiUrl() + "/#" + route.replace("{p}", String.valueOf(product.id()))
                .replace("{o}", String.valueOf(unpaidOrder)));
        driver.navigate().refresh();   // re-read the session from localStorage
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='" + readyTestId + "']")));

        Results results = new AxeBuilder().withTags(WCAG_21_AA).analyze(driver);
        List<Rule> violations = results.getViolations();
        Allure.addAttachment("axe violations - " + page, "text/plain", describe(violations));

        List<Rule> blocking = violations.stream().filter(v -> BLOCKING.contains(v.getImpact())).toList();
        assertTrue(blocking.isEmpty(), page + ": " + blocking.size() + " serious/critical violation(s)\n" + describe(blocking));
    }

    private static String describe(List<Rule> rules) {
        if (rules.isEmpty()) return "none";
        return rules.stream().map(r -> "[" + r.getImpact() + "] " + r.getId() + ": " + r.getHelp() + " (" + r.getHelpUrl() + ")\n"
                + r.getNodes().stream().limit(3).map(CheckedNode::getHtml)
                        .map(h -> "    " + (h.length() > 160 ? h.substring(0, 160) + "..." : h))
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n"));
    }
}
