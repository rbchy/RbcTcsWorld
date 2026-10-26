package com.rbctcsworld.ecommerce.qa.tests.ui;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import com.rbctcsworld.ecommerce.qa.drivers.DriverFactory;
import com.rbctcsworld.ecommerce.qa.pages.HomePage;
import com.rbctcsworld.ecommerce.qa.reporting.ScreenshotOnFailure;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Needs backend (8081) + frontend (npm run dev, 5173) running. Excluded with -DexcludedGroups=ui. */
@Tag("ui")
@Epic("Storefront UI")
@Feature("Home page")
class HomePageUiTest {

    private WebDriver driver;
    private HomePage home;

    @RegisterExtension
    ScreenshotOnFailure screenshots = new ScreenshotOnFailure(() -> driver);

    @BeforeEach
    void start() {
        driver = DriverFactory.create();
        home = new HomePage(driver).open();
    }

    @AfterEach
    void stop() {
        if (driver != null) driver.quit();
    }

    @Test
    @Tag("smoke")
    @DisplayName("Storefront loads with title and seeded products")
    void homeLoads() {
        assertAll(
                () -> assertTrue(home.title().equals("RbcTcsWorld Shop"), "title"),
                () -> assertTrue(home.productCount() >= 8, "seeded products visible"));
    }

    @Test
    @DisplayName("Catalog is paged: 20 products first, 'Load more' adds the rest (DEF-007)")
    void loadMore() {
        // own data: 21 products sharing a unique name prefix, so the test never depends on the catalog size
        String prefix = "LM" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        for (int i = 0; i < 21; i++) {
            new com.rbctcsworld.ecommerce.qa.api.ProductClient().create(com.rbctcsworld.ecommerce.qa.testdata.Fixtures.adminToken(),
                    com.rbctcsworld.ecommerce.qa.api.ProductClient.body(prefix + " lamp " + i, prefix + "-" + i, "qa",
                            new java.math.BigDecimal("9.99"), 3)).then().statusCode(201);
        }
        home.search(prefix);
        assertAll("first page",
                () -> assertEquals(21, home.catalogTotal()),
                () -> assertEquals(20, home.productCount()),
                () -> assertTrue(home.canLoadMore()));

        home.loadMore();
        assertAll("after Load more",
                () -> assertEquals(21, home.productCount()),
                () -> assertFalse(home.canLoadMore(), "button disappears on the last page"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ELEC-MOUSE-001", "ELEC-KEYB-002", "ELEC-MON-003", "ELEC-HUB-004",
            "HOME-BOTL-001", "HOME-MUG-002", "BOOK-QA-001", "ELEC-HEAD-005"})
    @DisplayName("Every seeded product shows its own picture, and it really loads")
    void seededProductPictures(String sku) {
        HomePage.Picture pic = home.pictureOf(sku);
        assertAll(sku,
                () -> assertEquals("/products/" + sku.toLowerCase() + ".svg", pic.src()),
                () -> assertFalse(pic.fallback(), "own picture, not the category one"),
                () -> assertTrue(pic.loaded(), "browser decoded the picture (no broken image)"));
    }

    @Test
    @DisplayName("A product without a picture shows the category picture instead of a broken image")
    void productWithoutPictureGetsTheFallback() {
        Fixtures.Product p = Fixtures.namedProduct("4.00", 2);   // category "qa", no imageUrl
        home.search(p.name());
        HomePage.Picture pic = home.pictureOf(p.sku());
        assertAll(() -> assertEquals("/products/product.svg", pic.src()),
                  () -> assertTrue(pic.fallback()),
                  () -> assertTrue(pic.loaded()));
    }

    @Test
    @DisplayName("Search filters the product grid")
    void searchFilters() {
        home.search("mouse");
        assertFalse(home.productNames().isEmpty(), "at least one result");
        assertTrue(home.productNames().stream().allMatch(n -> n.toLowerCase().contains("mouse")));
    }
}
