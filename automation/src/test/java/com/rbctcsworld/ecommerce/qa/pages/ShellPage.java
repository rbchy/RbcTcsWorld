package com.rbctcsworld.ecommerce.qa.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

/** Parts present on every page: top bar (language, help, account), sidebar menu, and the help center. */
public class ShellPage extends BasePage {

    public ShellPage(WebDriver driver) {
        super(driver);
    }

    public ShellPage open(String route) {
        openRoute(route);
        visible("nav-home");
        return this;
    }

    public ShellPage language(String code) {
        new Select(visible("lang-select")).selectByValue(code);
        wait.until(d -> code.equals(htmlAttribute("lang"))
                && code.equals(d.findElement(testId("lang-select")).getDomProperty("value")));
        return this;
    }

    public String htmlAttribute(String name) {
        return driver.findElement(By.tagName("html")).getDomAttribute(name);
    }

    public String menuLabel(String testId) {
        return text(testId);
    }

    /** Waits for the top bar to show this account (login and registration answer asynchronously). */
    public boolean waitLoggedInAs(String email) {
        return wait.until(d -> isShown("nav-user") && email.equals(textOrEmpty("nav-user")));
    }

    public void logout() {
        click("nav-logout");
    }

    /** Exit in the top bar: log out, goodbye page, and the browser is asked to close the window. */
    public ShellPage exit() {
        click("nav-exit");
        return this;
    }

    /** Phones: the menu sits behind the "Menu" button; waits until it has slid in. */
    public ShellPage openMenu() {
        click("menu-toggle");
        wait.until(d -> d.findElement(By.id("sidebar")).getDomAttribute("class").contains("open"));
        return this;
    }

    /** No sideways scrolling: the page is never wider than the screen. */
    public boolean fitsTheScreen() {
        return (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return document.documentElement.scrollWidth <= window.innerWidth;");
    }

    /** On phones Log out and Exit show only their icon; the text stays for screen readers. */
    public boolean topBarButtonsAreIconsOnly() {
        return driver.findElements(By.cssSelector("[data-testid^='nav-'] .btn-label")).stream()
                .allMatch(label -> "absolute".equals(label.getCssValue("position")));
    }

    /** Log out / Exit in the sidebar's Account section (desktop: the sidebar is always open; phones: openMenu first). */
    public void logoutFromMenu() {
        click("side-logout");
    }

    public ShellPage exitFromMenu() {
        click("side-exit");
        return this;
    }

    /** The goodbye page after Exit. A WebDriver window is never closed by the page itself, so it stays. */
    public boolean waitGoodbye() {
        return wait.until(d -> isShown("goodbye") && d.getCurrentUrl().endsWith("#/goodbye"));
    }

    public boolean goodbyeSaysSignedOut() {
        return isShown("goodbye-signed-out");
    }

    public ShellPage backToShop() {
        click("goodbye-back");
        visible("product-card");
        return this;
    }

    /** Waits until the top bar offers "Log in" again. */
    public boolean waitLoggedOut() {
        return wait.until(d -> isShown("nav-login") && !isShown("nav-user"));
    }

    public boolean adminMenuShown() {
        return isShown("admin-menu");
    }

    public ShellPage openHelp() {
        click("nav-help");
        visible("help-page");
        return this;
    }

    public String helpTitle() {
        return text("help-title");
    }

    public ShellPage searchHelp(String text) {
        type("help-search", text);
        return this;
    }

    public List<String> helpTopics() {
        return attributeOfAll("help-topic", "data-topic");
    }

    /** Waits until the help center lists exactly these topics (it filters while typing). */
    public boolean helpTopicsBecome(List<String> expected) {
        return wait.until(d -> expected.equals(d.findElements(testId("help-topic")).stream()
                .map(e -> e.getDomAttribute("data-topic")).toList()));
    }

    public boolean noHelpResults() {
        return isShown("help-empty");
    }

    public String bannerCode() {
        return text("banner-code");
    }
}
