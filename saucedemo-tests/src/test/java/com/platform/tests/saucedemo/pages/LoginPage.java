package com.platform.tests.saucedemo.pages;

import com.microsoft.playwright.Page;

/**
 * Page Object for the SauceDemo login page.
 */
public class LoginPage {

    private static final String USERNAME_FIELD = "#user-name";
    private static final String PASSWORD_FIELD = "#password";
    private static final String LOGIN_BUTTON   = "#login-button";
    private static final String ERROR_MESSAGE  = "[data-test='error']";

    private final Page page;

    public LoginPage(Page page) {
        this.page = page;
    }

    public void navigate(String baseUrl) {
        page.navigate(baseUrl);
        page.waitForSelector(LOGIN_BUTTON);
    }

    public void login(String username, String password) {
        page.fill(USERNAME_FIELD, username);
        page.fill(PASSWORD_FIELD, password);
        page.click(LOGIN_BUTTON);
    }

    public boolean isOnInventoryPage() {
        return page.url().contains("/inventory.html");
    }

    public String getErrorMessage() {
        return page.textContent(ERROR_MESSAGE);
    }

    public boolean hasError() {
        return page.isVisible(ERROR_MESSAGE);
    }
}
