package com.platform.tests.saucedemo.pages;

import com.microsoft.playwright.Page;

/**
 * Page Object covering Checkout Step One, Step Two, and the Confirmation page.
 */
public class CheckoutPage {

    private static final String FIRST_NAME    = "[data-test='firstName']";
    private static final String LAST_NAME     = "[data-test='lastName']";
    private static final String ZIP_CODE      = "[data-test='postalCode']";
    private static final String CONTINUE_BTN  = "[data-test='continue']";
    private static final String FINISH_BTN    = "[data-test='finish']";
    private static final String ITEM_TOTAL    = ".summary_subtotal_label";
    private static final String CONFIRMATION  = ".complete-header";

    private final Page page;

    public CheckoutPage(Page page) {
        this.page = page;
    }

    // ── Step One — shipping info ──────────────────────────────────────────────

    public void fillShippingInfo(String firstName, String lastName, String zip) {
        page.fill(FIRST_NAME, firstName);
        page.fill(LAST_NAME, lastName);
        page.fill(ZIP_CODE, zip);
        page.click(CONTINUE_BTN);
        page.waitForSelector(ITEM_TOTAL);
    }

    // ── Step Two — overview ───────────────────────────────────────────────────

    public double getItemTotal() {
        String text = page.textContent(ITEM_TOTAL);
        // text is like "Item total: $29.99"
        return Double.parseDouble(text.replaceAll("[^0-9.]", ""));
    }

    public void finishOrder() {
        page.click(FINISH_BTN);
        page.waitForSelector(CONFIRMATION);
    }

    // ── Confirmation ──────────────────────────────────────────────────────────

    public String getConfirmationMessage() {
        return page.textContent(CONFIRMATION).trim();
    }
}
