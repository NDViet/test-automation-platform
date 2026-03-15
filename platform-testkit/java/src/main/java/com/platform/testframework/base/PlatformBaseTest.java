package com.platform.testframework.base;

import com.platform.testframework.assertion.SoftAssert;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.extension.PlatformExtension;
import com.platform.testframework.extension.RetryExtension;
import com.platform.testframework.logging.TestLogger;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for all tests using the platform framework.
 *
 * <p>Wires {@link PlatformExtension} (context, MDC, tracing, publishing)
 * and {@link RetryExtension} ({@code @Retryable} support) automatically.</p>
 *
 * <h3>Extend for specific test types:</h3>
 * <ul>
 *   <li>{@code PlatformApiBaseTest} — pre-wired HTTP client with request/response logging</li>
 *   <li>{@code PlatformUiBaseTest}  — WebDriver lifecycle, auto-screenshot on failure</li>
 * </ul>
 *
 * <h3>Example:</h3>
 * <pre>{@code
 * @TestMetadata(owner = "payments-team", feature = "Checkout", severity = CRITICAL)
 * class CheckoutTest extends PlatformBaseTest {
 *
 *     @Test
 *     void userCanCompleteCheckout() {
 *         log.step("Add item to cart");
 *           cart.addItem(ITEM_ID);
 *         log.endStep();
 *
 *         log.step("Complete checkout");
 *           checkout.fillAddress(address);
 *           checkout.pay(card);
 *         log.endStep();
 *
 *         log.step("Verify order confirmation");
 *           assertThat(confirmation.getOrderId()).isNotNull();
 *         log.endStep();
 *     }
 * }
 * }</pre>
 */
@ExtendWith({PlatformExtension.class, RetryExtension.class})
public abstract class PlatformBaseTest {

    /**
     * Structured test logger — writes to SLF4J with MDC context AND captures
     * output to the test context for platform publishing.
     */
    protected final TestLogger log = TestLogger.forClass(getClass());

    /**
     * Returns the current {@link TestContext} (non-null inside a test method).
     * Use to add custom environment info or attachments programmatically.
     */
    protected TestContext context() {
        return TestContextHolder.require();
    }

    /**
     * Adds a custom environment key-value to the test result.
     * Useful for recording browser version, device, base URL, etc.
     *
     * <pre>{@code
     * env("browser", driver.getCapabilities().getBrowserName());
     * env("browser.version", driver.getCapabilities().getBrowserVersion());
     * env("app.url", System.getenv("APP_URL"));
     * }</pre>
     */
    protected void env(String key, String value) {
        TestContextHolder.require().putEnvironment(key, value);
    }

    /**
     * Convenience for soft assertion blocks.
     *
     * <pre>{@code
     * softly(soft -> {
     *     soft.assertThat(page.getTitle()).isEqualTo("Dashboard");
     *     soft.assertThat(page.getGreeting()).contains("Welcome");
     * });
     * }</pre>
     */
    protected void softly(java.util.function.Consumer<org.assertj.core.api.SoftAssertions> assertions) {
        SoftAssert.assertAll(assertions);
    }
}
