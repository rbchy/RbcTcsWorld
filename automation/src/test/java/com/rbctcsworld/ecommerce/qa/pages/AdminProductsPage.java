package com.rbctcsworld.ecommerce.qa.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** Store admin > Products (frontend/src/pages/admin/AdminProducts.jsx): add, edit, update, delete, search. */
public class AdminProductsPage extends BasePage {

    public AdminProductsPage(WebDriver driver) {
        super(driver);
    }

    public AdminProductsPage open() {
        openRoute("/admin/products");
        visible("admin-product-table");
        return this;
    }

    /** As a customer: the route answers with the "admins only" panel instead of the table. */
    public AdminProductsPage openAsCustomer() {
        openRoute("/admin/products");
        visible("admin-only");
        return this;
    }

    public AdminProductsPage startAdd() {
        click("admin-product-add");
        visible("product-form");
        return this;
    }

    public AdminProductsPage fill(String name, String sku, String category, String price, String stock) {
        type("pf-name", name);
        type("pf-sku", sku);
        type("pf-category", category);
        type("pf-price", price);
        type("pf-stock", stock);
        return this;
    }

    public void save() {
        click("pf-save");
    }

    public String fieldError(String field) {
        return textOrEmpty("pf-" + field + "-error");
    }

    public boolean formOpen() {
        return isShown("product-form");
    }

    public String notice() {
        return text("admin-notice");
    }

    public String error() {
        return text("admin-error");
    }

    /** Filters the table and waits until the row with this SKU is (or is no longer) listed. */
    public AdminProductsPage search(String text) {
        type("admin-product-search", text);
        return this;
    }

    private By row(String sku) {
        return By.cssSelector("[data-testid='admin-product-row'][data-sku='" + sku + "']");
    }

    private By inRow(String sku, String testId) {
        return By.cssSelector("[data-testid='admin-product-row'][data-sku='" + sku + "'] [data-testid='" + testId + "']");
    }

    public boolean waitForRow(String sku) {
        return wait.until(d -> !d.findElements(row(sku)).isEmpty());
    }

    public boolean waitUntilGone(String sku) {
        return wait.until(d -> d.findElements(row(sku)).isEmpty());
    }

    public String priceOf(String sku) {
        return wait.until(d -> d.findElement(inRow(sku, "apr-price")).getText().trim());
    }

    public String stockOf(String sku) {
        return wait.until(d -> d.findElement(inRow(sku, "apr-stock")).getText().trim());
    }

    public String nameOf(String sku) {
        return wait.until(d -> d.findElement(inRow(sku, "apr-name")).getText().trim());
    }

    public AdminProductsPage edit(String sku) {
        wait.until(d -> d.findElement(inRow(sku, "apr-edit"))).click();
        visible("product-form");
        return this;
    }

    public void changePriceAndStock(String price, String stock) {
        type("pf-price", price);
        type("pf-stock", stock);
        save();
    }

    public void delete(String sku) {
        wait.until(d -> d.findElement(inRow(sku, "apr-delete"))).click();
        visible("delete-confirm");
        click("apr-delete-yes");
    }

    public boolean adminOnlyShown() {
        return isShown("admin-only");
    }
}
