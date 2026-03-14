package com.platform.tests.saucedemo.context;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Per-scenario Playwright context — shared across all step definition classes
 * via Cucumber PicoContainer DI.
 *
 * <p>Lifecycle is managed by {@link com.platform.tests.saucedemo.hooks.BrowserHooks}:
 * <ul>
 *   <li>{@code @Before} — launches browser, creates Page, stores it here</li>
 *   <li>{@code @After}  — captures screenshot on failure, closes everything</li>
 * </ul>
 */
public class ScenarioContext {

    private static final String BASE_URL = "https://www.saucedemo.com";

    private Playwright playwright;
    private Browser browser;
    private BrowserContext browserContext;
    private Page page;

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Page page() { return page; }

    public String baseUrl() { return BASE_URL; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void init(boolean headless) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(headless)
        );
        browserContext = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1280, 800)
        );
        page = browserContext.newPage();
    }

    public byte[] screenshot() {
        if (page != null) {
            return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        }
        return null;
    }

    public void teardown() {
        if (browserContext != null) browserContext.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
