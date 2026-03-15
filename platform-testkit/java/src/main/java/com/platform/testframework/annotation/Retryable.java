package com.platform.testframework.annotation;

import java.lang.annotation.*;

/**
 * Marks a test method for automatic retry on failure.
 *
 * <p>Processed by {@link com.platform.testframework.extension.RetryExtension}.
 * Each failed attempt is published to the platform with the attempt number recorded.</p>
 *
 * <pre>{@code
 * @Test
 * @Retryable(maxAttempts = 3)
 * void flakyIntegrationTest() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retryable {
    /** Maximum number of total attempts (including the first run). Default: 3. */
    int maxAttempts() default 3;
}
