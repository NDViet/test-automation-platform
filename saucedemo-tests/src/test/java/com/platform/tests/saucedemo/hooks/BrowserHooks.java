package com.platform.tests.saucedemo.hooks;

import com.platform.tests.saucedemo.context.ScenarioContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber hooks that manage the Playwright browser lifecycle.
 *
 * <p>Receives {@link ScenarioContext} via PicoContainer injection — the same
 * instance shared with all step definition classes in the same scenario.</p>
 */
public class BrowserHooks {

    private static final Logger log = LoggerFactory.getLogger(BrowserHooks.class);

    private final ScenarioContext ctx;

    public BrowserHooks(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Before(order = 0)
    public void startBrowser(Scenario scenario) {
        boolean headless = !"false".equalsIgnoreCase(
                System.getProperty("playwright.headless", "true"));
        log.info("[Browser] Starting Chromium headless={} for scenario '{}'",
                headless, scenario.getName());
        ctx.init(headless);
    }

    @After(order = 0)
    public void teardownBrowser(Scenario scenario) {
        if (scenario.isFailed()) {
            byte[] screenshot = ctx.screenshot();
            if (screenshot != null) {
                scenario.attach(screenshot, "image/png", "screenshot-on-failure");
                log.warn("[Browser] Scenario '{}' FAILED — screenshot attached", scenario.getName());
            }
        }
        ctx.teardown();
        log.info("[Browser] Closed browser for scenario '{}'", scenario.getName());
    }
}
