package com.platform.testframework.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Parameter;

/**
 * JUnit5 {@code @RegisterExtension} that manages the full Playwright browser
 * lifecycle and integrates with the platform framework.
 *
 * <h3>What it does:</h3>
 * <ul>
 *   <li>Creates a Playwright instance, browser, and context before each test</li>
 *   <li>Attaches {@link PlatformPlaywrightSupport} listeners (console errors,
 *       network failures, page errors) to every page</li>
 *   <li>Auto-captures a full-page screenshot on test failure</li>
 *   <li>Closes browser/playwright cleanly after each test</li>
 * </ul>
 *
 * <h3>Usage — JUnit5 (works alongside PlatformExtension):</h3>
 * <pre>{@code
 * @ExtendWith(PlatformExtension.class)   // platform context + publishing
 * class LoginTest {
 *
 *     @RegisterExtension
 *     PlatformPlaywrightExtension pw = PlatformPlaywrightExtension.chromium();
 *
 *     @Test
 *     void userCanLogin(Page page) {
 *         page.navigate(APP_URL + "/login");
 *         page.locator("#username").fill("admin");
 *         page.locator("#password").fill("secret");
 *         page.locator("[type=submit]").click();
 *         assertThat(page.locator(".dashboard-title")).isVisible();
 *     }
 * }
 * }</pre>
 *
 * <h3>Usage — integrated with PlatformBaseTest:</h3>
 * <pre>{@code
 * class LoginTest extends PlatformBaseTest {
 *
 *     @RegisterExtension
 *     PlatformPlaywrightExtension pw = PlatformPlaywrightExtension.chromium()
 *             .headless(false)      // show browser in local dev
 *             .slowMo(100);         // slow down for debugging
 *
 *     @Test
 *     void userCanLogin(Page page) {
 *         log.step("Navigate to login");
 *           page.navigate(APP_URL + "/login");
 *         log.endStep();
 *
 *         log.step("Submit credentials");
 *           page.fill("#username", "admin");
 *           page.fill("#password", "secret");
 *           page.click("[type=submit]");
 *         log.endStep();
 *     }
 * }
 * }</pre>
 */
public class PlatformPlaywrightExtension
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Logger log = LoggerFactory.getLogger(PlatformPlaywrightExtension.class);

    // ── Config ───────────────────────────────────────────────────────────────

    public enum BrowserType { CHROMIUM, FIREFOX, WEBKIT }

    private BrowserType browserType = BrowserType.CHROMIUM;
    private boolean headless = true;
    private double  slowMo   = 0;
    private int     width    = 1920;
    private int     height   = 1080;

    // ── Lifecycle state (per test) ────────────────────────────────────────────

    private Playwright       playwright;
    private Browser          browser;
    private BrowserContext   context;
    private Page             page;

    // ── Factory methods ───────────────────────────────────────────────────────

    public static PlatformPlaywrightExtension chromium() {
        return new PlatformPlaywrightExtension();
    }

    public static PlatformPlaywrightExtension firefox() {
        var ext = new PlatformPlaywrightExtension();
        ext.browserType = BrowserType.FIREFOX;
        return ext;
    }

    public static PlatformPlaywrightExtension webkit() {
        var ext = new PlatformPlaywrightExtension();
        ext.browserType = BrowserType.WEBKIT;
        return ext;
    }

    // ── Builder-style config ──────────────────────────────────────────────────

    public PlatformPlaywrightExtension headless(boolean headless) {
        this.headless = headless;
        return this;
    }

    public PlatformPlaywrightExtension slowMo(double ms) {
        this.slowMo = ms;
        return this;
    }

    public PlatformPlaywrightExtension viewport(int width, int height) {
        this.width  = width;
        this.height = height;
        return this;
    }

    // ── JUnit5 lifecycle ──────────────────────────────────────────────────────

    @Override
    public void beforeEach(ExtensionContext ctx) {
        playwright = Playwright.create();
        com.microsoft.playwright.BrowserType.LaunchOptions opts = new com.microsoft.playwright.BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(slowMo);

        browser = switch (browserType) {
            case FIREFOX  -> playwright.firefox().launch(opts);
            case WEBKIT   -> playwright.webkit().launch(opts);
            default       -> playwright.chromium().launch(opts);
        };

        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(width, height));
        page = context.newPage();

        // Attach platform listeners (console errors, network failures, page errors)
        PlatformPlaywrightSupport.attach(page);

        log.info("[Platform/Playwright] Browser started — {} headless={}", browserType, headless);
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        try {
            // Auto-screenshot on failure
            if (ctx.getExecutionException().isPresent() && page != null) {
                String testName = ctx.getRequiredTestMethod().getName();
                PlatformPlaywrightSupport.screenshotOnFailure(page, testName);
                PlatformPlaywrightSupport.capturePageSource(page, testName);
            }
        } finally {
            closeSafely();
        }
    }

    // ── ParameterResolver — inject Page into test methods ────────────────────

    @Override
    public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        Parameter param = paramCtx.getParameter();
        return param.getType() == Page.class
                || param.getType() == BrowserContext.class
                || param.getType() == Browser.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        Class<?> type = paramCtx.getParameter().getType();
        if (type == Page.class)           return page;
        if (type == BrowserContext.class) return context;
        if (type == Browser.class)        return browser;
        throw new IllegalArgumentException("Cannot resolve: " + type);
    }

    // ── Accessors — for PlatformTestNGBase / Cucumber subclasses ─────────────

    /** The current test page. Valid only within a test method. */
    public Page getPage()               { return page; }
    public BrowserContext getContext()  { return context; }
    public Browser getBrowser()         { return browser; }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void closeSafely() {
        try { if (context    != null) context.close();    } catch (Exception ignored) {}
        try { if (browser    != null) browser.close();    } catch (Exception ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Exception ignored) {}
        page = null; context = null; browser = null; playwright = null;
        log.debug("[Platform/Playwright] Browser closed");
    }
}
