package com.example.theinternet.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.platform.testframework.logging.TestLogger;

/**
 * Page object for {@code /checkboxes}.
 *
 * <p>The page renders two checkboxes.  Initial state (as of the live site):</p>
 * <ul>
 *   <li>Checkbox 1 — <b>unchecked</b></li>
 *   <li>Checkbox 2 — <b>checked</b></li>
 * </ul>
 */
public class CheckboxesPage extends BasePage {

    private static final String CHECKBOXES = "#checkboxes input[type='checkbox']";

    public CheckboxesPage(Page page, TestLogger log) {
        super(page, log);
    }

    public CheckboxesPage open() {
        log.info("Navigating to /checkboxes");
        page.navigate(url("/checkboxes"));
        return this;
    }

    /** Returns the nth checkbox locator (1-based index). */
    public Locator checkbox(int n) {
        return page.locator(CHECKBOXES).nth(n - 1);
    }

    public boolean isChecked(int n) {
        return checkbox(n).isChecked();
    }

    /** Clicks the nth checkbox to toggle its state. */
    public void toggle(int n) {
        log.info("Toggling checkbox {}", n);
        checkbox(n).click();
    }

    /** Returns the total number of checkboxes on the page. */
    public int count() {
        return page.locator(CHECKBOXES).count();
    }
}
