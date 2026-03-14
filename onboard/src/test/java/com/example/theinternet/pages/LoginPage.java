package com.example.theinternet.pages;

import com.microsoft.playwright.Page;
import com.platform.testframework.logging.TestLogger;

/**
 * Page object for {@code /login} — Form Authentication.
 *
 * <p>Valid credentials: username {@code tomsmith}, password {@code SuperSecretPassword!}</p>
 */
public class LoginPage extends BasePage {

    private static final String USERNAME   = "#username";
    private static final String PASSWORD   = "#password";
    private static final String SUBMIT     = "button[type=submit]";
    private static final String FLASH_MSG  = "#flash";
    private static final String LOGOUT_BTN = "a[href='/logout']";

    public LoginPage(Page page, TestLogger log) {
        super(page, log);
    }

    public LoginPage open() {
        log.info("Navigating to /login");
        page.navigate(url("/login"));
        return this;
    }

    public void loginWith(String username, String password) {
        log.info("Filling credentials — username={}", username);
        page.fill(USERNAME, username);
        page.fill(PASSWORD, password);
        page.click(SUBMIT);
    }

    /** Returns the flash message text (success or error banner). */
    public String flashMessage() {
        return page.locator(FLASH_MSG).innerText().strip();
    }

    /** True when the browser has been redirected to /secure. */
    public boolean isOnSecurePage() {
        return page.url().endsWith("/secure");
    }

    public boolean isLogoutVisible() {
        return page.locator(LOGOUT_BTN).isVisible();
    }
}
