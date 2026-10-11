package com.rbctcsworld.ecommerce.qa.tests.ui;

import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.AdminProductsPage;
import com.rbctcsworld.ecommerce.qa.pages.BasePage;
import com.rbctcsworld.ecommerce.qa.pages.ShellPage;
import com.rbctcsworld.ecommerce.qa.reporting.ScreenshotOnFailure;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Store admin UI: product add / edit / update / delete with client-side validation, checked against the API
 * afterwards (the UI is only proof if the backend really holds the change). Customers never see the admin area.
 */
@Tag("ui")
@Epic("Storefront UI")
@Feature("Store admin")
class UiAdminProductsTest {

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

    private AdminProductsPage asAdmin() {
        BasePage.loginWithToken(driver, Fixtures.adminToken(), TestConfig.adminEmail(), "ADMIN");
        return new AdminProductsPage(driver).open();
    }

    @Test
    @DisplayName("Admin adds, updates and deletes a product; the API confirms every step")
    void productLifecycle() {
        String sku = TestData.uniqueSku();
        AdminProductsPage page = asAdmin();

        // add, with validation first: an empty form is stopped in the browser, nothing reaches the API
        page.startAdd().save();
        assertAll("validation",
                () -> assertFalse(page.fieldError("name").isBlank(), "name required"),
                () -> assertFalse(page.fieldError("sku").isBlank(), "sku required"),
                () -> assertFalse(page.fieldError("price").isBlank(), "price required"),
                () -> assertTrue(page.formOpen(), "form stays open"));
        page.fill("QA desk fan", sku, "home", "-3", "7").save();
        assertFalse(page.fieldError("price").isBlank(), "negative price rejected");
        page.fill("QA desk fan", sku, "home", "24.50", "7").save();
        assertEquals("Product saved", page.notice());

        page.search(sku).waitForRow(sku);
        assertAll(() -> assertEquals("$24.50", page.priceOf(sku)), () -> assertEquals("7", page.stockOf(sku)));
        Map<String, Object> created = findBySku(sku);
        assertEquals(24.5, ((Number) created.get("price")).doubleValue(), 0.001, "API has the new product");

        // update
        page.edit(sku).changePriceAndStock("19.99", "12");
        assertEquals("Product updated", page.notice());
        assertAll(() -> assertEquals("$19.99", page.priceOf(sku)), () -> assertEquals("12", page.stockOf(sku)));
        Map<String, Object> updated = findBySku(sku);
        assertAll("API after update",
                () -> assertEquals(19.99, ((Number) updated.get("price")).doubleValue(), 0.001),
                () -> assertEquals(12, ((Number) updated.get("stock")).intValue()));

        // delete (soft): gone from the admin list and from the public catalog
        page.delete(sku);
        assertEquals("Product deleted", page.notice());
        assertTrue(page.waitUntilGone(sku));
        assertTrue(new ProductClient().page(sku, 0, 5, "id").jsonPath().getList("sku").isEmpty(), "hidden from customers");
    }

    @Test
    @DisplayName("Duplicate SKU: the server's 409 message is shown and the form keeps the input")
    void duplicateSku() {
        Fixtures.Product existing = Fixtures.namedProduct("5.00", 3);
        AdminProductsPage page = asAdmin();
        page.startAdd().fill("Copy", existing.sku(), "", "5.00", "1").save();
        assertAll(() -> assertFalse(page.error().isBlank(), "error shown"),
                  () -> assertTrue(page.formOpen(), "form still open for a correction"));
    }

    @Test
    @DisplayName("Customer sees neither the admin menu nor the admin pages")
    void customerHasNoAdminArea() {
        Fixtures.Customer c = Fixtures.newCustomer();
        BasePage.loginWithToken(driver, c.token(), c.email());
        ShellPage shell = new ShellPage(driver).open("/");
        assertFalse(shell.adminMenuShown(), "no admin menu");
        assertTrue(new AdminProductsPage(driver).openAsCustomer().adminOnlyShown(), "admin page is refused");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findBySku(String sku) {
        Response r = new ProductClient().page(sku, 0, 5, "id");
        List<Map<String, Object>> rows = r.jsonPath().getList("$");
        return rows.stream().filter(p -> sku.equals(p.get("sku"))).findFirst()
                .orElseThrow(() -> new AssertionError("product " + sku + " not found via API"));
    }
}
