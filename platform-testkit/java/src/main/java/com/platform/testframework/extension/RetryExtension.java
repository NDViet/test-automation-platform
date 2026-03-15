package com.platform.testframework.extension;

import com.platform.testframework.annotation.Retryable;
import com.platform.testframework.context.TestContextHolder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries {@link Retryable}-annotated tests on failure.
 *
 * <p>Each failed attempt increments the retry counter in {@link
 * com.platform.testframework.context.TestContext} so the platform tracks flaky behaviour.
 * Only the final attempt outcome is published as the test result.</p>
 *
 * <p>Register alongside {@link PlatformExtension}:</p>
 * <pre>{@code
 * @ExtendWith({PlatformExtension.class, RetryExtension.class})
 * class MyTest { ... }
 * }</pre>
 */
public class RetryExtension implements TestExecutionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryExtension.class);

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(RetryExtension.class);
    private static final String KEY_ATTEMPTS = "attempts";

    @Override
    public void handleTestExecutionException(ExtensionContext ctx, Throwable throwable) throws Throwable {
        Retryable retryable = ctx.getRequiredTestMethod().getAnnotation(Retryable.class);
        if (retryable == null) {
            throw throwable; // not retryable — propagate immediately
        }

        int maxAttempts = retryable.maxAttempts();
        int attempt = ctx.getStore(NS).getOrDefault(KEY_ATTEMPTS, Integer.class, 1);

        if (attempt < maxAttempts) {
            log.warn("[Retry] Test failed on attempt {}/{} — retrying: {}",
                    attempt, maxAttempts, ctx.getDisplayName());

            ctx.getStore(NS).put(KEY_ATTEMPTS, attempt + 1);

            // Track in context for platform publishing
            var testCtx = TestContextHolder.get();
            if (testCtx != null) {
                testCtx.incrementRetry();
            }

            // Re-invoke the test method
            ctx.getRequiredTestMethod().invoke(ctx.getRequiredTestInstance());
        } else {
            log.error("[Retry] Test failed after {}/{} attempts: {}",
                    attempt, maxAttempts, ctx.getDisplayName());
            throw throwable;
        }
    }
}
