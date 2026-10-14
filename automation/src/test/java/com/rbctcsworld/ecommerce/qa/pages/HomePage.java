package com.rbctcsworld.ecommerce.qa.pages;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/** Storefront home page (frontend/src/pages/Home.jsx). */
public class HomePage extends BasePage {

    private static final By SEARCH = testId("search-input");
    private static final By PRODUCT_CARD = testId("product-card");
    private static final By PRODUCT_NAME = testId("product-name");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage open() {
        driver.get(TestConfig.uiUrl() + "/#/");
        driver.navigate().refresh();   // re-read localStorage session after a token login
        visible(SEARCH);
        return this;
    }

    public HomePage search(String text) {
        WebElement box = visible(SEARCH);
        box.clear();
        box.sendKeys(text);
        // wait until every visible card matches the search (results are filtered by the API)
        wait.until(d -> productNames().stream().allMatch(n -> n.toLowerCase().contains(text.toLowerCase())));
        return this;
    }

    public int productCount() {
        wait.until(d -> !all(PRODUCT_CARD).isEmpty());
        return all(PRODUCT_CARD).size();
    }

    public List<String> productNames() {
        return wait.until(d -> d.findElements(PRODUCT_NAME).stream().map(WebElement::getText).toList());
    }

    /** Sets the quantity on the card of the given SKU and clicks "Add to cart". */
    public HomePage addToCart(String sku, int quantity) {
        By card = By.cssSelector("[data-testid='product-card'][data-sku='" + sku + "']");
        WebElement c = visible(card);
        WebElement qty = c.findElement(testId("qty-input"));
        qty.clear();
        qty.sendKeys(String.valueOf(quantity));
        c.findElement(testId("add-to-cart")).click();
        return this;
    }

    /** Clicks the heart on the card of the given SKU and waits for the confirmation message. */
    public HomePage addToWishlist(String sku) {
        By card = By.cssSelector("[data-testid='product-card'][data-sku='" + sku + "']");
        String before = textOrEmpty("flash") + "|" + textOrEmpty("error-banner");
        visible(card).findElement(testId("add-to-wishlist")).click();
        wait.until(d -> !(textOrEmpty("flash") + "|" + textOrEmpty("error-banner")).equals(before));
        return this;
    }

    /** The picture on a product card: its address, whether it is the category fallback, and whether it loaded. */
    public record Picture(String src, boolean fallback, boolean loaded) {
    }

    public Picture pictureOf(String sku) {
        By img = By.cssSelector("[data-testid='product-card'][data-sku='" + sku + "'] [data-testid='product-image']");
        visible(img);
        boolean loaded = pictureLoaded(img);   // a failed link switches to the fallback first, so read src afterwards
        WebElement e = driver.findElement(img);
        return new Picture(e.getDomAttribute("src"), "true".equals(e.getDomAttribute("data-fallback")), loaded);
    }

    /** "4.5" or "0.0" from the card's rating element. */
    public String ratingOf(String sku) {
        By rating = By.cssSelector("[data-testid='product-card'][data-sku='" + sku + "'] [data-testid='product-rating']");
        return wait.until(d -> d.findElement(rating).getDomAttribute("data-average"));
    }

    /** "Showing 20 of 245 products" -> 245 */
    public int catalogTotal() {
        productCount();                                  // wait for the first page, otherwise "0 of 0"
        return Integer.parseInt(text("catalog-total").replaceAll(".* of (\\d+) products.*", "$1"));
    }

    public boolean canLoadMore() {
        return isShown("load-more");
    }

    /** Clicks "Load more" and waits until more cards are on the page. */
    public HomePage loadMore() {
        int before = all(PRODUCT_CARD).size();
        click("load-more");
        wait.until(d -> all(PRODUCT_CARD).size() > before);
        return this;
    }

    public String flash() {
        return text("flash");
    }

    public String error() {
        return text("error-banner");
    }

    public CartPage goToCart() {
        clickMenuItem("nav-cart");
        return new CartPage(driver).waitLoaded();
    }

    public boolean isLoggedInAs(String email) {
        return isShown("nav-user") && text("nav-user").equals(email);
    }
}
