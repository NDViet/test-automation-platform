package com.platform.tests.saucedemo.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import java.util.List;

/**
 * Page Object for the SauceDemo inventory / products page.
 */
public class InventoryPage {

    private static final String INVENTORY_ITEM       = ".inventory_item";
    private static final String ITEM_NAME            = ".inventory_item_name";
    private static final String ITEM_PRICE           = ".inventory_item_price";
    private static final String SORT_DROPDOWN        = "[data-test='product-sort-container']";
    private static final String CART_BADGE           = ".shopping_cart_badge";
    private static final String ADD_TO_CART_BTN_TMPL = "[data-test='add-to-cart-%s']";
    private static final String REMOVE_BTN_TMPL      = "[data-test='remove-%s']";
    private static final String CART_LINK            = ".shopping_cart_link";

    private final Page page;

    public InventoryPage(Page page) {
        this.page = page;
    }

    public void navigate(String baseUrl) {
        page.navigate(baseUrl + "/inventory.html");
        page.waitForSelector(INVENTORY_ITEM);
    }

    public int getProductCount() {
        return page.locator(INVENTORY_ITEM).count();
    }

    public List<String> getProductNames() {
        return page.locator(ITEM_NAME).allTextContents();
    }

    public List<Double> getProductPrices() {
        return page.locator(ITEM_PRICE).allTextContents().stream()
                .map(p -> Double.parseDouble(p.replace("$", "").trim()))
                .toList();
    }

    public boolean allProductsHaveNamePriceAndButton() {
        int items  = page.locator(INVENTORY_ITEM).count();
        int names  = page.locator(ITEM_NAME).count();
        int prices = page.locator(ITEM_PRICE).count();
        int btns   = page.locator(".btn_inventory").count();
        return names == items && prices == items && btns == items;
    }

    public void sortBy(String option) {
        page.selectOption(SORT_DROPDOWN, option);
    }

    public void addToCart(String productName) {
        String dataTestId = toDataTestId(productName);
        page.click(String.format(ADD_TO_CART_BTN_TMPL, dataTestId));
    }

    public boolean isButtonRemove(String productName) {
        String dataTestId = toDataTestId(productName);
        return page.isVisible(String.format(REMOVE_BTN_TMPL, dataTestId));
    }

    public String getCartBadgeText() {
        Locator badge = page.locator(CART_BADGE);
        return badge.isVisible() ? badge.textContent() : "";
    }

    public boolean isCartBadgeVisible() {
        return page.isVisible(CART_BADGE);
    }

    public void goToCart() {
        page.click(CART_LINK);
    }

    /** Converts product name to the kebab-case data-test ID used by SauceDemo. */
    private static String toDataTestId(String productName) {
        return productName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
