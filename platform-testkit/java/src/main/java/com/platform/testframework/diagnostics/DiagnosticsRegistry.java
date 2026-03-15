package com.platform.testframework.diagnostics;

import java.util.Optional;

/**
 * Thread-local registry for a {@link DiagnosticsProvider}.
 *
 * <p>Each test thread registers its own provider during setup so that the
 * platform listener can capture browser state on failure without holding a
 * reference to any Selenium/Playwright type.</p>
 *
 * <h3>Usage (in BaseTest):</h3>
 * <pre>{@code
 * @BeforeMethod
 * public void setUp() {
 *     WebUI.openBrowser(BASE_URL);
 *     DiagnosticsRegistry.register(
 *         () -> DriverManager.getInstance().getDriver().getPageSource(),
 *         () -> ((TakesScreenshot) DriverManager.getInstance().getDriver())
 *                   .getScreenshotAs(OutputType.BYTES)
 *     );
 * }
 *
 * @AfterMethod
 * public void tearDown(ITestResult result) {
 *     DiagnosticsRegistry.unregister();
 *     WebUI.closeBrowser();
 * }
 * }</pre>
 */
public final class DiagnosticsRegistry {

    private static final ThreadLocal<DiagnosticsProvider> CURRENT = new ThreadLocal<>();

    private DiagnosticsRegistry() {}

    /** Register a provider for the current test thread. */
    public static void register(DiagnosticsProvider provider) {
        CURRENT.set(provider);
    }

    /**
     * Convenience factory — register inline lambdas without an explicit class.
     *
     * @param pageSourceSupplier  supplies page HTML (may return null on error)
     * @param screenshotSupplier  supplies PNG bytes (may return null on error)
     */
    public static void register(PageSourceSupplier pageSourceSupplier,
                                ScreenshotSupplier screenshotSupplier) {
        CURRENT.set(new DiagnosticsProvider() {
            @Override
            public String capturePageSource() {
                try { return pageSourceSupplier.get(); } catch (Exception e) { return null; }
            }
            @Override
            public byte[] captureScreenshot() {
                try { return screenshotSupplier.get(); } catch (Exception e) { return null; }
            }
        });
    }

    /** Returns the provider registered on the current thread, if any. */
    public static Optional<DiagnosticsProvider> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Remove the registration for the current thread (call in teardown). */
    public static void unregister() {
        CURRENT.remove();
    }

    @FunctionalInterface
    public interface PageSourceSupplier {
        String get() throws Exception;
    }

    @FunctionalInterface
    public interface ScreenshotSupplier {
        byte[] get() throws Exception;
    }
}
