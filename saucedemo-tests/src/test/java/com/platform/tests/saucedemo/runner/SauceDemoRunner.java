package com.platform.tests.saucedemo.runner;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit Platform Suite runner for SauceDemo E2E tests.
 *
 * <p>Uses {@code cucumber.properties} for plugin/glue config so CI can
 * override via system properties without recompiling.
 *
 * <p>Run specific tags from CLI:
 * <pre>
 *   mvn test -pl saucedemo-tests -Dcucumber.filter.tags="@smoke"
 *   mvn test -pl saucedemo-tests -Pheaded          # headed browser
 * </pre>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
        key = GLUE_PROPERTY_NAME,
        value = "com.platform.tests.saucedemo"
)
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty,com.platform.testframework.cucumber.PlatformCucumberPlugin"
)
public class SauceDemoRunner {
    // Entry point — no code needed; JUnit Platform Suite handles discovery.
}
