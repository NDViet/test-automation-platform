package com.platform.testframework.playwright;

import com.microsoft.playwright.Page;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Framework-agnostic Playwright support — works with JUnit5, TestNG, Cucumber,
 * and any custom framework that uses {@link TestContextHolder}.
 *
 * <h3>What it does when attached to a {@link Page}:</h3>
 * <ul>
 *   <li><b>Console errors</b> — captured to the test log (helps spot JS exceptions)</li>
 *   <li><b>Page errors</b> — uncaught JS exceptions logged as [PAGE ERROR]</li>
 *   <li><b>Network failures</b> — failed requests logged with URL + status</li>
 *   <li><b>Screenshots on failure</b> — auto-captured via {@link #screenshotOnFailure}</li>
 * </ul>
 *
 * <h3>Usage — any framework:</h3>
 * <pre>{@code
 * // In @BeforeEach / @BeforeMethod / @Before hook:
 * Page page = browser.newPage();
 * PlatformPlaywrightSupport.attach(page);
 *
 * // After test, on failure:
 * PlatformPlaywrightSupport.screenshotOnFailure(page, "my-test");
 * }</pre>
 *
 * <h3>Usage — JUnit5 with managed lifecycle:</h3>
 * <pre>{@code
 * @RegisterExtension
 * PlatformPlaywrightExtension pw = PlatformPlaywrightExtension.chromium();
 *
 * @Test
 * void myTest(Page page) {
 *     page.navigate("https://example.com");
 * }
 * }</pre>
 */
public final class PlatformPlaywrightSupport {

    private static final Logger log = LoggerFactory.getLogger(PlatformPlaywrightSupport.class);

    private PlatformPlaywrightSupport() {}

    /**
     * Attaches platform listeners to a Playwright {@link Page}.
     *
     * <p>Call once after creating the page, before the test runs.
     * Safe to call multiple times on the same page (Playwright deduplicates listeners).</p>
     */
    public static void attach(Page page) {
        if (page == null) return;

        // ── Console errors ────────────────────────────────────────────────
        page.onConsoleMessage(msg -> {
            String type = msg.type();
            if ("error".equalsIgnoreCase(type) || "warning".equalsIgnoreCase(type)) {
                String line = "[BROWSER " + type.toUpperCase() + "] " + msg.text();
                log.warn(line);
                appendToContext(line);
            }
        });

        // ── Uncaught JS exceptions ────────────────────────────────────────
        page.onPageError(error -> {
            String line = "[PAGE ERROR] " + error;
            log.error(line);
            appendToContext(line);
        });

        // ── Network failures ──────────────────────────────────────────────
        page.onRequestFailed(request -> {
            String line = "[NETWORK FAIL] " + request.method()
                    + " " + request.url()
                    + " → " + request.failure();
            log.warn(line);
            appendToContext(line);
        });

        // ── Log navigations (useful for tracing flow) ─────────────────────
        page.onLoad(p -> {
            String line = "[PAGE LOAD] " + p.url();
            log.debug(line);
            appendToContext(line);
        });

        log.debug("[Platform/Playwright] Listeners attached to page");
    }

    /**
     * Captures a full-page screenshot and attaches it to the current test context.
     *
     * <p>Typically called in the test's afterEach on failure. If no context is active,
     * the screenshot is still taken and its path is logged.</p>
     *
     * @param page  the Playwright page to screenshot
     * @param label descriptive label for the attachment (used in the filename)
     * @return the path to the screenshot file, or null if capture failed
     */
    public static Path screenshot(Page page, String label) {
        if (page == null) return null;
        try {
            Path file = Files.createTempFile("platform-pw-" + sanitize(label) + "-", ".png");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(file)
                    .setFullPage(true));
            String path = file.toAbsolutePath().toString();
            log.info("[Platform/Playwright] Screenshot saved: {}", path);
            TestContext ctx = TestContextHolder.get();
            if (ctx != null) {
                ctx.addAttachment(path);
                ctx.appendLog("[SCREENSHOT] " + label + " → " + path);
            }
            return file;
        } catch (IOException e) {
            log.warn("[Platform/Playwright] Failed to capture screenshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Captures a screenshot only when the test has failed.
     * Called automatically by {@link PlatformPlaywrightExtension} on failure.
     */
    public static Path screenshotOnFailure(Page page, String testName) {
        return screenshot(page, "failure-" + testName);
    }

    /**
     * Captures the current page HTML source and attaches it to the context.
     * Useful for debugging dynamic rendering issues.
     */
    public static void capturePageSource(Page page, String label) {
        if (page == null) return;
        try {
            String html = page.content();
            Path file = Files.createTempFile("platform-pw-source-" + sanitize(label) + "-", ".html");
            Files.writeString(file, html);
            TestContext ctx = TestContextHolder.get();
            if (ctx != null) {
                ctx.addAttachment(file.toAbsolutePath().toString());
                ctx.appendLog("[PAGE SOURCE] " + label + " → " + file);
            }
        } catch (Exception e) {
            log.warn("[Platform/Playwright] Failed to capture page source: {}", e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void appendToContext(String line) {
        TestContext ctx = TestContextHolder.get();
        if (ctx != null) ctx.appendLog(line);
    }

    private static String sanitize(String s) {
        return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
