package com.example.saucedemo.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

/**
 * Page object for {@code /inventory.html} — the main product listing page.
 */
public class InventoryPage extends BasePage {

    private static final By PAGE_TITLE     = By.cssSelector(".title");
    private static final By INVENTORY_ITEM = By.cssSelector(".inventory_item");
    private static final By ITEM_NAME      = By.cssSelector(".inventory_item_name");
    private static final By SORT_DROPDOWN  = By.cssSelector(".product_sort_container");
    private static final By CART_BADGE     = By.cssSelector(".shopping_cart_badge");
    private static final By CART_LINK      = By.cssSelector(".shopping_cart_link");

    public InventoryPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        try {
            wait.until(ExpectedConditions.urlContains("/inventory.html"));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public String pageTitle() {
        return waitForVisible(PAGE_TITLE).getText();
    }

    public int productCount() {
        return driver.findElements(INVENTORY_ITEM).size();
    }

    public void sortBy(String visibleOptionText) {
        log.info("Sorting products by '{}'", visibleOptionText);
        Select select = new Select(waitForVisible(SORT_DROPDOWN));
        select.selectByVisibleText(visibleOptionText);
    }

    public String firstProductName() {
        List<WebElement> names = driver.findElements(ITEM_NAME);
        return names.isEmpty() ? "" : names.get(0).getText();
    }

    /**
     * Clicks the "Add to cart" button for the named product.
     * The data-test attribute follows the pattern
     * {@code add-to-cart-<slug>} where the slug is the product name
     * lower-cased with spaces and dots replaced by hyphens.
     */
    public void addToCart(String productName) {
        log.info("Adding '{}' to cart", productName);
        String slug   = toSlug(productName);
        By     button = By.cssSelector("[data-test='add-to-cart-" + slug + "']");
        waitForClickable(button).click();
    }

    public void removeFromCart(String productName) {
        log.info("Removing '{}' from cart (inventory page)", productName);
        String slug   = toSlug(productName);
        By     button = By.cssSelector("[data-test='remove-" + slug + "']");
        waitForClickable(button).click();
    }

    public String cartBadgeText() {
        return waitForVisible(CART_BADGE).getText();
    }

    public boolean isCartBadgeVisible() {
        try {
            return driver.findElement(CART_BADGE).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public void goToCart() {
        log.info("Navigating to cart");
        waitForClickable(CART_LINK).click();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Converts a product display name to the slug used in data-test attributes.
     * e.g. "Sauce Labs Backpack"          → "sauce-labs-backpack"
     *      "Test.allTheThings() T-Shirt"  → "test.allthethings()-t-shirt"
     */
    private static String toSlug(String productName) {
        return productName.toLowerCase().replace(" ", "-");
    }
}
