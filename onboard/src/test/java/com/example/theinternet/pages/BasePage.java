package com.example.theinternet.pages;

import com.microsoft.playwright.Page;
import com.platform.testframework.logging.TestLogger;

/**
 * Base class for all page objects.
 *
 * <p>Page objects encapsulate locator strategies and page interactions so that
 * tests remain readable and locator changes are isolated to one place.</p>
 *
 * <p>The {@link TestLogger} is passed in so that page actions can emit
 * structured log lines that appear both in SLF4J output and in the platform
 * test context (visible in the platform UI per test).</p>
 */
public abstract class BasePage {

    protected final Page       page;
    protected final TestLogger log;

    protected BasePage(Page page, TestLogger log) {
        this.page = page;
        this.log  = log;
    }

    /** Builds the full URL for a site-relative path, e.g. {@code "/login"}. */
    protected String url(String path) {
        return "https://the-internet.herokuapp.com" + path;
    }
}
