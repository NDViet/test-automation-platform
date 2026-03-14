package com.platform.testframework.logging;

import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.step.TestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Fluent test logging API.
 *
 * <p>Every call writes to the SLF4J logger (with MDC context for structured log
 * aggregation) <em>and</em> appends to the active {@link TestContext} so the
 * output is attached to the platform result for AI-assisted debugging.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * // In PlatformBaseTest:
 * protected final TestLogger log = TestLogger.forClass(getClass());
 *
 * // In a test:
 * log.step("Navigate to login page");
 *   log.info("URL: {}", baseUrl);
 *   driver.get(baseUrl + "/login");
 * log.endStep();
 *
 * log.step("Submit credentials");
 *   log.info("Username: {}", username);
 *   loginPage.submit(username, password);
 * log.endStep();
 * }</pre>
 *
 * <h3>Attachments:</h3>
 * <pre>{@code
 * log.attach("screenshot.png", screenshotBytes, "image/png");
 * }</pre>
 */
public final class TestLogger {

    private final Logger logger;

    private TestLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public static TestLogger forClass(Class<?> clazz) {
        return new TestLogger(clazz);
    }

    // -------------------------------------------------------------------------
    // Step lifecycle
    // -------------------------------------------------------------------------

    /**
     * Opens a named step. Must be balanced with {@link #endStep()}.
     * Steps can be nested — use for keyword-driven or page-object style testing.
     *
     * @return the opened {@link TestStep} (usually ignored; for advanced use)
     */
    public TestStep step(String description, Object... args) {
        String name = format(description, args);
        logger.info("[STEP] {}", name);
        TestContext ctx = TestContextHolder.get();
        if (ctx != null) {
            MDC.put("step", name);
            ctx.appendLog("[STEP] " + name);
            return ctx.pushStep(name);
        }
        return new TestStep(name); // noop if called outside a test
    }

    /** Closes the currently open step, marking it PASSED. */
    public void endStep() {
        MDC.remove("step");
        TestContext ctx = TestContextHolder.get();
        if (ctx != null) {
            ctx.popStep();
        }
    }

    /**
     * Convenience: runs {@code action} inside a named step and closes it automatically.
     *
     * <pre>{@code
     * log.step("Login as admin", () -> {
     *     loginPage.enterUsername("admin");
     *     loginPage.enterPassword("secret");
     *     loginPage.clickLogin();
     * });
     * }</pre>
     */
    public void step(String description, Runnable action) {
        step(description);
        try {
            action.run();
            endStep();
        } catch (AssertionError | RuntimeException e) {
            TestContext ctx = TestContextHolder.get();
            if (ctx != null && !ctx.pushStep("").getStatus().toString().isEmpty()) {
                ctx.popStep();
            }
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Log levels
    // -------------------------------------------------------------------------

    public void info(String message, Object... args) {
        logger.info(message, args);
        appendToContext("INFO  " + format(message, args));
    }

    public void warn(String message, Object... args) {
        logger.warn(message, args);
        appendToContext("WARN  " + format(message, args));
    }

    public void error(String message, Object... args) {
        logger.error(message, args);
        appendToContext("ERROR " + format(message, args));
    }

    public void debug(String message, Object... args) {
        logger.debug(message, args);
        // debug not appended to context (too verbose for platform storage)
    }

    // -------------------------------------------------------------------------
    // Attachments
    // -------------------------------------------------------------------------

    /**
     * Attaches binary data (e.g. screenshots, HAR files) to the current test.
     * The bytes are saved to a temp file and the path is recorded in the context.
     */
    public void attach(String name, byte[] data, String mimeType) {
        try {
            String suffix = mimeType.contains("png") ? ".png"
                    : mimeType.contains("json") ? ".json"
                    : mimeType.contains("html") ? ".html" : ".bin";
            var tmpFile = java.nio.file.Files.createTempFile("platform-attach-", suffix);
            java.nio.file.Files.write(tmpFile, data);
            String path = tmpFile.toAbsolutePath().toString();
            logger.info("[ATTACH] {} → {}", name, path);
            TestContext ctx = TestContextHolder.get();
            if (ctx != null) {
                ctx.addAttachment(path);
                ctx.appendLog("[ATTACH] " + name + " → " + path);
            }
        } catch (Exception e) {
            logger.warn("[ATTACH] Failed to save attachment '{}': {}", name, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void appendToContext(String line) {
        TestContext ctx = TestContextHolder.get();
        if (ctx != null) {
            ctx.appendLog(line);
        }
    }

    private static String format(String template, Object... args) {
        if (args == null || args.length == 0) return template;
        // Simple SLF4J-style {} substitution
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        int i = 0;
        while (i < template.length()) {
            if (i < template.length() - 1
                    && template.charAt(i) == '{'
                    && template.charAt(i + 1) == '}'
                    && argIdx < args.length) {
                sb.append(args[argIdx++]);
                i += 2;
            } else {
                sb.append(template.charAt(i++));
            }
        }
        return sb.toString();
    }
}
