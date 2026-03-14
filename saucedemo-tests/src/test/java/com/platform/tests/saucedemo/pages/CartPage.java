package com.platform.tests.saucedemo.pages;

import com.microsoft.playwright.Page;

import java.util.List;

/**
 * Page Object for the SauceDemo cart page.
 */
public class CartPage {

    private static final String CART_ITEM       = ".cart_item";
    private static final String ITEM_NAME       = ".inventory_item_name";
    private static final String CHECKOUT_BUTTON = "[data-test='checkout']";
    private static final String CONTINUE_SHOPPING = "[data-test='continue-shopping']";
    private static final String REMOVE_BTN_TMPL = "[data-test='remove-%s']";
    private static final String CART_BADGE      = ".shopping_cart_badge";

    private final Page page;

    public CartPage(Page page) {
        this.page = page;
    }

    public List<String> getCartItemNames() {
        return page.locator(ITEM_NAME).allTextContents();
    }

    public int getCartItemCount() {
        return page.locator(CART_ITEM).count();
    }

    public boolean containsItem(String productName) {
        return getCartItemNames().stream()
                .anyMatch(name -> name.equalsIgnoreCase(productName));
    }

    public boolean isEmpty() {
        return page.locator(CART_ITEM).count() == 0;
    }

    public boolean isCartBadgeVisible() {
        return page.isVisible(CART_BADGE);
    }

    public void removeItem(String productName) {
        String dataTestId = toDataTestId(productName);
        page.click(String.format(REMOVE_BTN_TMPL, dataTestId));
    }

    public void proceedToCheckout() {
        page.click(CHECKOUT_BUTTON);
        page.waitForSelector("[data-test='firstName']");
    }

    private static String toDataTestId(String productName) {
        return productName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
