package com.example.saucedemo.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.List;

/**
 * Page object for {@code /cart.html}.
 */
public class CartPage extends BasePage {

    private static final By CART_ITEM          = By.cssSelector(".cart_item");
    private static final By ITEM_NAME          = By.cssSelector(".inventory_item_name");
    private static final By CONTINUE_SHOPPING  = By.id("continue-shopping");
    private static final By CHECKOUT_BUTTON    = By.id("checkout");

    public CartPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return driver.getCurrentUrl().contains("/cart.html");
    }

    /** Returns names of all items currently in the cart. */
    public List<String> itemNames() {
        return driver.findElements(ITEM_NAME)
                .stream()
                .map(e -> e.getText())
                .toList();
    }

    public int itemCount() {
        return driver.findElements(CART_ITEM).size();
    }

    public boolean containsItem(String productName) {
        return itemNames().contains(productName);
    }

    public boolean isEmpty() {
        return driver.findElements(CART_ITEM).isEmpty();
    }

    /**
     * Clicks the remove button for the given product.
     * The data-test attribute pattern is {@code remove-<slug>}.
     */
    public void removeItem(String productName) {
        log.info("Removing '{}' from cart page", productName);
        String slug   = productName.toLowerCase().replace(" ", "-");
        By     button = By.cssSelector("[data-test='remove-" + slug + "']");
        waitForClickable(button).click();
    }

    public void continueShopping() {
        log.info("Clicking 'Continue Shopping'");
        waitForClickable(CONTINUE_SHOPPING).click();
    }

    public void proceedToCheckout() {
        log.info("Clicking 'Checkout'");
        waitForClickable(CHECKOUT_BUTTON).click();
    }
}
