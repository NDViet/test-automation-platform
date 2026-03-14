package com.example.theinternet.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.platform.testframework.logging.TestLogger;

/**
 * Page object for {@code /dynamic_loading}.
 *
 * <p>Covers two variants:</p>
 * <ul>
 *   <li><b>Example 1</b> ({@code /dynamic_loading/1}) — element is present in the DOM
 *       but hidden; clicking Start makes it visible.</li>
 *   <li><b>Example 2</b> ({@code /dynamic_loading/2}) — element does not exist until
 *       the loading sequence completes and renders it into the DOM.</li>
 * </ul>
 *
 * <p>Both are excellent tests for Playwright's built-in auto-waiting.
 * If the wait times out, {@link com.platform.testframework.classify.FailureClassifier}
 * will flag the failure as {@code FLAKY_TIMING} and emit a hint explaining the
 * timeout context.</p>
 */
public class DynamicLoadingPage extends BasePage {

    private static final String START_BUTTON = "#start button";
    private static final String LOADING      = "#loading";
    private static final String FINISH       = "#finish h4";

    public DynamicLoadingPage(Page page, TestLogger log) {
        super(page, log);
    }

    /** Navigates to Example 1 — hidden element. */
    public DynamicLoadingPage openExample1() {
        log.info("Navigating to /dynamic_loading/1 (hidden element)");
        page.navigate(url("/dynamic_loading/1"));
        return this;
    }

    /** Navigates to Example 2 — element rendered after loading. */
    public DynamicLoadingPage openExample2() {
        log.info("Navigating to /dynamic_loading/2 (element rendered after delay)");
        page.navigate(url("/dynamic_loading/2"));
        return this;
    }

    public void clickStart() {
        log.info("Clicking Start button");
        page.click(START_BUTTON);
    }

    /**
     * Waits for the loading spinner to disappear, then returns the finish-text.
     *
     * <p>Playwright's {@code waitFor(HIDDEN)} blocks until the {@code #loading}
     * div is no longer visible — this is the idiomatic way to wait for async
     * operations without arbitrary {@code Thread.sleep} calls.</p>
     */
    public String finishText() {
        log.info("Waiting for loading spinner to disappear");
        page.locator(LOADING).waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

        String text = page.locator(FINISH).innerText().strip();
        log.info("Finish text appeared: '{}'", text);
        return text;
    }
}
