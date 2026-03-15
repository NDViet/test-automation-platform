package com.platform.testframework.diagnostics;

/**
 * Framework-agnostic contract for capturing live browser state at the moment
 * a test fails due to a locator/element issue.
 *
 * <p>Test frameworks register an implementation via {@link DiagnosticsRegistry}
 * in their {@code @BeforeMethod} / {@code @BeforeEach} setup, and unregister in
 * teardown.  The platform-testkit calls this interface on {@code BAD_LOCATOR}
 * failures without importing Selenium, Playwright, or any browser library.</p>
 *
 * <h3>Selenium example:</h3>
 * <pre>{@code
 * DiagnosticsRegistry.register(() -> driver.getPageSource(),
 *                               () -> ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
 * }</pre>
 */
public interface DiagnosticsProvider {

    /**
     * Returns the full HTML source of the currently loaded page.
     * May return {@code null} or an empty string if the page is not accessible.
     */
    String capturePageSource();

    /**
     * Returns a PNG screenshot of the current browser viewport as raw bytes.
     * May return {@code null} if a screenshot cannot be taken.
     */
    byte[] captureScreenshot();
}
