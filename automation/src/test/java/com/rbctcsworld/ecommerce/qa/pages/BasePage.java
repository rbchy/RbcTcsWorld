package com.rbctcsworld.ecommerce.qa.pages;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Explicit waits only (no implicit waits / Thread.sleep) - the main cause of flaky UI tests.
 * Every locator uses the data-testid attribute the frontend exposes, so CSS/text changes never break tests.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        // React re-renders replace DOM nodes; a reference found a moment ago can become "stale".
        // Every explicit wait simply looks the element up again instead of failing.
        this.wait.ignoring(StaleElementReferenceException.class);
    }

    protected static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    protected WebElement visible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement visible(String testId) {
        return visible(testId(testId));
    }

    protected List<WebElement> all(By locator) {
        return driver.findElements(locator);
    }

    protected void click(String testId) {
        wait.until(ExpectedConditions.elementToBeClickable(testId(testId))).click();
    }

    /**
     * Replaces the field's text by typing like a user. No modifier keys (Cmd/Ctrl+A): Safari keeps Cmd
     * pressed after such a chord, so every later key and click became Cmd+key (empty fields, links opening
     * in a new tab - DEF-024). Backspace + Delete once per character clear the field wherever the caret is,
     * and fire React's onChange like real typing (WebElement.clear() does not).
     */
    protected void type(String testId, String text) {
        WebElement e = visible(testId);
        String old = e.getDomProperty("value");
        if (old != null && !old.isEmpty()) {
            e.sendKeys(Keys.BACK_SPACE.toString().repeat(old.length()) + Keys.DELETE.toString().repeat(old.length()));
        }
        if (!text.isEmpty()) e.sendKeys(text);
        // guard: fail here with a clear message instead of later with a confusing one
        new WebDriverWait(driver, Duration.ofSeconds(3))
                .ignoring(StaleElementReferenceException.class)
                .withMessage("field '" + testId + "' should contain '" + text + "'")
                .until(d -> text.equals(d.findElement(testId(testId)).getDomProperty("value")));
    }

    /** Finds the element and reads its text in ONE retried step, so a re-render in between cannot break it. */
    protected String text(String testId) {
        By by = testId(testId);
        return wait.until(d -> {
            WebElement e = d.findElement(by);
            return e.isDisplayed() ? e.getText().trim() : null;
        });
    }

    /** Text of the element, or "" when it is not on the page (never waits, never throws on a re-render). */
    protected String textOrEmpty(String testId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                List<WebElement> found = all(testId(testId));
                return found.isEmpty() || !found.get(0).isDisplayed() ? "" : found.get(0).getText().trim();
            } catch (StaleElementReferenceException e) {
                // element replaced between find and read - look again
            }
        }
        return "";
    }

    protected boolean isShown(String testId) {
        try {
            List<WebElement> found = all(testId(testId));
            return !found.isEmpty() && found.get(0).isDisplayed();
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    /** Reads an attribute of every matching element; retried as a whole if the list re-renders meanwhile. */
    protected List<String> attributeOfAll(String testId, String attribute) {
        return wait.until(d -> d.findElements(testId(testId)).stream().map(e -> e.getDomAttribute(attribute)).toList());
    }

    /** Opens a client-side route such as "/cart" or "/orders/12". */
    protected void openRoute(String route) {
        driver.get(TestConfig.uiUrl() + "/#" + route);
    }

    /**
     * Fast login for tests whose subject is NOT the login page: put the JWT (from an API call) into
     * localStorage exactly like the app does after a real login. Saves ~2s and removes a dependency per test.
     */
    public static void loginWithToken(WebDriver driver, String token, String email) {
        loginWithToken(driver, token, email, "CUSTOMER");
    }

    /** Same, with the role the backend returned at login (ADMIN shows the store-admin menu). */
    public static void loginWithToken(WebDriver driver, String token, String email, String role) {
        loginWithTokens(driver, token, null, email, role);
    }

    /** Access token + refresh token, as the storefront keeps them after a real login. */
    public static void loginWithTokens(WebDriver driver, String token, String refreshToken, String email, String role) {
        driver.get(TestConfig.uiUrl() + "/#/");
        ((JavascriptExecutor) driver).executeScript(
                "localStorage.setItem('token', arguments[0]); localStorage.setItem('email', arguments[1]);"
                        + "localStorage.setItem('role', arguments[2]);"
                        + "if (arguments[3]) localStorage.setItem('refreshToken', arguments[3]);",
                token, email, role, refreshToken);
        // The app reads the session once at start-up, and a later driver.get() that only changes the
        // #route does not reload the page, so reload here: every caller then starts logged in.
        driver.navigate().refresh();
    }

    /** A value the storefront keeps in localStorage (token, refreshToken, email, role). */
    public String stored(String key) {
        return (String) ((JavascriptExecutor) driver).executeScript("return localStorage.getItem(arguments[0]);", key);
    }

    public String title() {
        return driver.getTitle();
    }
}
