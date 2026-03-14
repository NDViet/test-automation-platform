package com.platform.testframework.annotation;

import java.lang.annotation.*;

/**
 * Declares which production classes a test case covers.
 * Used by Test Impact Analysis to determine which tests to run for a given code change.
 *
 * <p>Apply at class level for all tests in the class, or at method level for a single test.
 * Method-level annotations take precedence over class-level.</p>
 *
 * <pre>{@code
 * @AffectedBy({"com.example.PaymentService", "com.example.CartService"})
 * class CheckoutTest extends PlatformBaseTest {
 *
 *     @AffectedBy("com.example.InvoiceService")
 *     void testInvoiceGeneration() { ... }
 * }
 * }</pre>
 *
 * <p>The platform captures these class names during test execution and stores them in the
 * coverage mapping table. When a PR changes {@code PaymentService.java}, the impact API
 * returns {@code CheckoutTest} as a recommended test to run.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AffectedBy {

    /**
     * Fully qualified class names of production classes covered by this test.
     * Supports wildcards: {@code "com.example.payment.*"} matches all classes in that package.
     */
    String[] value();
}
