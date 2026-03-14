package com.example.saucedemo.runner;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * TestNG-based Cucumber runner for the SauceDemo test suite.
 *
 * <p>Registers two report plugins:</p>
 * <ul>
 *   <li><b>json</b> – writes {@code target/cucumber-reports/cucumber.json} for
 *       local HTML reports.</li>
 *   <li><b>PlatformCucumberPlugin</b> (testframework) – publishes each scenario
 *       natively after it runs, including per-step results, execution mode,
 *       CI provider, and failure hints. Does not depend on the JSON file.</li>
 * </ul>
 *
 * <p>Run a subset of scenarios:</p>
 * <pre>
 *   mvn test -Dcucumber.filter.tags="@smoke"
 *   mvn test -Dcucumber.filter.tags="@login or @cart"
 * </pre>
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue     = {
                "com.example.saucedemo.hooks",
                "com.example.saucedemo.steps"
        },
        plugin = {
                "pretty",
                "html:target/cucumber-reports/cucumber-html.html",
                "json:target/cucumber-reports/cucumber.json",
                // Native per-scenario publishing: execution mode, step results,
                // CI metadata, failure hints — no JSON file required
                "com.platform.testframework.cucumber.PlatformCucumberPlugin"
        },
        monochrome = true
)
public class CucumberRunner extends AbstractTestNGCucumberTests {

    /**
     * Allows parallel scenario execution when running with TestNG parallel data providers.
     * Comment out {@code parallel = true} to run scenarios sequentially.
     */
    @Override
    @DataProvider(parallel = false)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
