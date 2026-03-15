package com.platform.testframework.session;

import com.platform.common.enums.TestStatus;
import com.platform.sdk.config.PlatformConfig;
import com.platform.testframework.classify.FailureClassifier;
import com.platform.testframework.classify.FailureHint;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.logging.TestLogger;
import com.platform.testframework.report.EnvironmentInfo;
import com.platform.testframework.report.NativeReportPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Manual lifecycle API for custom and hybrid test frameworks.
 *
 * <p>Use this when you are not using JUnit5 {@code @ExtendWith}, TestNG
 * {@code @Listeners}, or Cucumber plugins — for example, in a bespoke
 * keyword-driven harness, a RestAssured API test suite with its own runner,
 * or a custom Playwright wrapper that doesn't use JUnit5.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * PlatformTestSession session = PlatformTestSession.start("LoginTest#userCanLogin");
 * try {
 *     session.step("Navigate to login page", () -> {
 *         page.navigate(baseUrl + "/login");
 *     });
 *
 *     session.step("Submit credentials", () -> {
 *         page.fill("#username", "admin");
 *         page.fill("#password", "secret");
 *         page.click("[type=submit]");
 *     });
 *
 *     session.step("Verify dashboard", () -> {
 *         assertThat(page.title()).contains("Dashboard");
 *     });
 *
 *     session.success();
 * } catch (Throwable t) {
 *     session.failure(t);
 *     throw t;
 * } finally {
 *     session.close();   // publishes to platform; clears thread-local
 * }
 * }</pre>
 *
 * <h3>With try-with-resources:</h3>
 * <pre>{@code
 * try (PlatformTestSession session = PlatformTestSession.start("LoginTest#userCanLogin")) {
 *     session.step("do something", () -> { ... });
 *     session.success();
 *     // failure() is called automatically in close() if success() was never called
 * }
 * }</pre>
 *
 * <h3>Tags and environment metadata:</h3>
 * <pre>{@code
 * PlatformTestSession session = PlatformTestSession.builder("LoginTest#canLogin")
 *         .tags(List.of("smoke", "auth"))
 *         .env("browser", "chromium")
 *         .env("baseUrl", "https://staging.example.com")
 *         .build();
 * }</pre>
 */
public final class PlatformTestSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PlatformTestSession.class);

    private final TestContext   ctx;
    private final TestLogger    logger;
    private final long          startMs;
    private final PlatformConfig config;

    private TestStatus  resultStatus;
    private Throwable   resultThrowable;
    private boolean     closed = false;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Start a session for {@code testId} (format: {@code ClassName#methodName}).
     * Tags and display name are derived from the testId.
     */
    public static PlatformTestSession start(String testId) {
        return builder(testId).build();
    }

    /** Builder with optional tags and extra environment entries. */
    public static Builder builder(String testId) {
        return new Builder(testId);
    }

    // ── Step execution ────────────────────────────────────────────────────────

    /**
     * Run {@code action} inside a named step.
     *
     * <p>The step is pushed to the platform context, executed, then popped.
     * If the action throws, the step is marked as failed and the exception
     * is re-thrown.</p>
     */
    public void step(String name, ThrowingRunnable action) throws Throwable {
        ctx.pushStep(name);
        MDC.put("step", name);
        try {
            log.info("[STEP] {}", name);
            action.run();
            ctx.popStep();
        } catch (Throwable t) {
            ctx.appendLog("[STEP FAILED] " + name + " → " + t.getMessage());
            ctx.popStep();
            throw t;
        } finally {
            MDC.remove("step");
        }
    }

    /**
     * Run {@code action} inside a named step, checked exceptions wrapped in
     * {@link RuntimeException}.
     */
    public void stepUnchecked(String name, ThrowingRunnable action) {
        try {
            step(name, action);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // ── Result recording ──────────────────────────────────────────────────────

    /** Mark this session as passed. */
    public void success() {
        this.resultStatus = TestStatus.PASSED;
    }

    /** Mark this session as failed with the given throwable. */
    public void failure(Throwable t) {
        this.resultThrowable = t;
        this.resultStatus = (t instanceof AssertionError)
                ? TestStatus.FAILED : TestStatus.BROKEN;
    }

    /** Mark this session as skipped. */
    public void skip() {
        this.resultStatus = TestStatus.SKIPPED;
    }

    /** Append an arbitrary log line to the captured test log. */
    public void log(String message) {
        ctx.appendLog(message);
    }

    /** Add an attachment (path to a file) to this test. */
    public void attach(String filePath) {
        ctx.addAttachment(filePath);
    }

    /** Set an environment/metadata key-value for this test run. */
    public void env(String key, String value) {
        ctx.putEnvironment(key, value);
    }

    /** Returns the underlying {@link TestLogger} for structured logging. */
    public TestLogger logger() {
        return logger;
    }

    /** Returns the underlying {@link TestContext} for advanced usage. */
    public TestContext context() {
        return ctx;
    }

    // ── AutoCloseable — publish and clean up ──────────────────────────────────

    /**
     * Publishes the test result to the platform and clears the thread-local context.
     *
     * <p>If neither {@link #success()} nor {@link #failure(Throwable)} was called
     * before {@code close()}, the session is treated as broken.</p>
     *
     * <p>Safe to call multiple times — subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;

        if (resultStatus == null) {
            resultStatus = TestStatus.BROKEN;
            ctx.appendLog("[SESSION] Closed without explicit success/failure — marking BROKEN.");
        }

        long durationMs = System.currentTimeMillis() - startMs;

        // Failure classification
        if (resultThrowable != null) {
            TestContext.Snapshot snap = ctx.snapshot();
            String lastStep = snap.rootSteps().isEmpty() ? null
                    : snap.rootSteps().get(snap.rootSteps().size() - 1).getName();
            FailureHint hint = FailureClassifier.classify(
                    resultThrowable, lastStep, snap.capturedLog());
            ctx.putEnvironment("platform.hint.category",   hint.category().name());
            ctx.putEnvironment("platform.hint.confidence", String.format("%.2f", hint.confidence()));
            ctx.putEnvironment("platform.hint.message",    hint.message());
        }

        TestContext.Snapshot snap = ctx.snapshot();
        log.info("[Platform/Session] Test finished — {} status={} duration={}ms",
                snap.testId(), resultStatus, durationMs);

        try {
            new NativeReportPublisher(config).publish(
                    snap, resultStatus,
                    resultThrowable != null ? resultThrowable.getMessage() : null,
                    stackTrace(resultThrowable),
                    durationMs
            );
        } catch (Exception e) {
            log.warn("[Platform/Session] Failed to publish result: {}", e.getMessage());
        }

        TestContextHolder.clear();
        MDC.remove("testId");
        MDC.remove("testClass");
        MDC.remove("testMethod");
        MDC.remove("traceId");
        MDC.remove("step");
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private final String     testId;
        private List<String>     tags         = List.of();
        private String           displayName;

        private Builder(String testId) {
            this.testId      = testId;
            this.displayName = testId;
        }

        public Builder tags(List<String> tags) {
            this.tags = List.copyOf(tags);
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public PlatformTestSession build() {
            return new PlatformTestSession(testId, displayName, tags);
        }
    }

    // ── Internal constructor ──────────────────────────────────────────────────

    private PlatformTestSession(String testId, String displayName, List<String> tags) {
        this.config  = PlatformConfig.load();
        this.startMs = System.currentTimeMillis();

        String traceId = UUID.randomUUID().toString().replace("-", "");

        // Parse className / methodName from "ClassName#methodName"
        String className  = testId.contains("#") ? testId.substring(0, testId.indexOf('#')) : testId;
        String methodName = testId.contains("#") ? testId.substring(testId.indexOf('#') + 1) : testId;

        MDC.put("testId",     testId);
        MDC.put("testClass",  className);
        MDC.put("testMethod", methodName);
        MDC.put("traceId",    traceId);

        this.ctx = new TestContext(
                testId, displayName, className, methodName, tags,
                traceId, config.getTeamId(), config.getProjectId()
        );
        this.ctx.putAllEnvironment(EnvironmentInfo.collect());
        TestContextHolder.set(ctx);

        this.logger = TestLogger.forClass(PlatformTestSession.class);

        log.info("[Platform/Session] Test started — {} traceId={}", testId, traceId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String stackTrace(Throwable t) {
        if (t == null) return null;
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    // ── ThrowingRunnable ──────────────────────────────────────────────────────

    /** A {@link Runnable} variant that permits checked exceptions. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
