package com.platform.testframework.annotation;

import java.lang.annotation.*;

/**
 * Optional metadata attached to a test class or method.
 * Flows through to the platform as tags, enabling filtering and reporting.
 *
 * <pre>{@code
 * @TestMetadata(owner = "payments-team", severity = TestMetadata.Severity.CRITICAL,
 *               feature = "Checkout", story = "PLAT-123")
 * class CheckoutFlowTest extends PlatformBaseTest { ... }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TestMetadata {

    /** Owning team or person. */
    String owner() default "";

    /** Business feature being tested. */
    String feature() default "";

    /** Story / ticket reference (e.g. JIRA key). */
    String story() default "";

    /** Severity of the test in production context. */
    Severity severity() default Severity.NORMAL;

    enum Severity { BLOCKER, CRITICAL, NORMAL, MINOR, TRIVIAL }
}
