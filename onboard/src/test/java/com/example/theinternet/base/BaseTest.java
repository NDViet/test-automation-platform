package com.example.theinternet.base;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.platform.testframework.playwright.PlatformPlaywrightSupport;
import com.platform.testframework.testng.PlatformTestNGBase;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

/**
 * Base class for all The-Internet tests.
 *
 * <p>Extends {@link PlatformTestNGBase} which wires in {@link
 * com.platform.testframework.testng.PlatformTestNGListener} automatically.
 * The listener initialises the platform test context before each test,
 * classifies failures, and publishes the result to the platform ingestion
 * service when each test finishes.</p>
 *
 * <p>This class owns the Playwright browser lifecycle — one fresh browser
 * context per test method to keep tests isolated. {@link PlatformPlaywrightSupport}
 * is attached to every page to capture:</p>
 * <ul>
 *   <li>Browser console errors / warnings</li>
 *   <li>Network failures (failed requests)</li>
 *   <li>Uncaught JavaScript exceptions</li>
 *   <li>Full-page screenshots and HTML source on failure</li>
 * </ul>
 */
public abstract class BaseTest extends PlatformTestNGBase {

    protected static final String BASE_URL = "https://the-internet.herokuapp.com";

    protected Playwright     playwright;
    protected Browser        browser;
    protected BrowserContext context;
    protected Page           page;

    // Exposed for subclass use in env() calls once the test context is active
    protected boolean headlessMode;

    @BeforeMethod(alwaysRun = true)
    public void setUpBrowser(Method method) {
        playwright = Playwright.create();

        headlessMode = Boolean.parseBoolean(System.getProperty("headless", "true"));

        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(headlessMode));

        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1280, 800));

        page = context.newPage();

        // Attach platform listeners: console errors, network failures, page errors, load events
        PlatformPlaywrightSupport.attach(page);

        // Note: env() cannot be called here because PlatformTestNGListener.onTestStart
        // (which initialises the TestContext) fires AFTER @BeforeMethod completes.
        // Call recordBrowserEnv() inside your @Test method if you want browser metadata
        // in the platform report, or just call env() directly from within each test.
        log.info("Browser ready — chromium headless={}", headlessMode);
    }

    /**
     * Records browser environment metadata into the platform test context.
     * Call this at the start of a {@code @Test} method (after the context is active).
     */
    protected void recordBrowserEnv() {
        env("browser",  "chromium");
        env("headless", String.valueOf(headlessMode));
        env("base.url", BASE_URL);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownBrowser(ITestResult result) {
        try {
            // Note: PlatformTestNGListener publishes the result (and clears the context)
            // in onTestSuccess/onTestFailure, which fires before @AfterMethod.
            // Screenshots taken here are still saved to disk and their path is logged,
            // but they are not attached to the already-published platform report.
            // To attach screenshots to the report, take them inside the @Test method
            // or via a TestNG Reporter listener that fires before the platform listener.
            if (!result.isSuccess() && page != null) {
                PlatformPlaywrightSupport.screenshotOnFailure(page, result.getMethod().getMethodName());
                PlatformPlaywrightSupport.capturePageSource(page, result.getMethod().getMethodName());
            }
        } finally {
            if (context    != null) context.close();
            if (browser    != null) browser.close();
            if (playwright != null) playwright.close();
        }
    }
}
