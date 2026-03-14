package com.platform.testframework.testng;

import com.platform.testframework.assertion.SoftAssert;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.logging.TestLogger;
import org.testng.annotations.Listeners;

/**
 * Base class for TestNG tests using the platform framework.
 *
 * <p>Pre-wires {@link PlatformTestNGListener} so subclasses just extend this
 * and get structured logging, tracing, step tracking, and platform publishing
 * automatically.</p>
 *
 * <h3>Example:</h3>
 * <pre>{@code
 * public class LoginTest extends PlatformTestNGBase {
 *
 *     @Test(groups = "smoke")
 *     public void userCanLogin() {
 *         log.step("Navigate to login page");
 *           driver.get(baseUrl + "/login");
 *         log.endStep();
 *
 *         log.step("Submit credentials");
 *           loginPage.login("user@example.com", "password");
 *         log.endStep();
 *
 *         log.step("Verify dashboard");
 *           softly(soft -> soft.assertThat(driver.getTitle()).contains("Dashboard"));
 *         log.endStep();
 *     }
 * }
 * }</pre>
 */
@Listeners(PlatformTestNGListener.class)
public abstract class PlatformTestNGBase {

    /** Structured test logger — writes to SLF4J with MDC + captures to platform context. */
    protected final TestLogger log = TestLogger.forClass(getClass());

    /** Returns the active test context. Safe to call in @Test methods only. */
    protected TestContext context() {
        return TestContextHolder.require();
    }

    /**
     * Adds a custom environment key-value (browser version, device, base URL, etc.)
     * to the test result sent to the platform.
     */
    protected void env(String key, String value) {
        TestContextHolder.require().putEnvironment(key, value);
    }

    /**
     * Runs a soft assertion block — collects all failures and reports them together.
     */
    protected void softly(java.util.function.Consumer<org.assertj.core.api.SoftAssertions> assertions) {
        SoftAssert.assertAll(assertions);
    }
}
