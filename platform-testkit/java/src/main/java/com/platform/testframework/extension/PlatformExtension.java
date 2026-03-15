package com.platform.testframework.extension;

import com.platform.common.enums.TestStatus;
import com.platform.sdk.config.PlatformConfig;
import com.platform.testframework.annotation.AffectedBy;
import com.platform.testframework.annotation.TestMetadata;
import com.platform.testframework.classify.FailureCategory;
import com.platform.testframework.classify.FailureClassifier;
import com.platform.testframework.classify.FailureHint;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.context.RunContext;
import com.platform.testframework.diagnostics.DiagnosticsRegistry;
import com.platform.testframework.diagnostics.LocatorAiAnalyzer;
import com.platform.testframework.report.EnvironmentInfo;
import com.platform.testframework.report.NativeReportPublisher;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JUnit 5 extension that wires all platform-testkit-java capabilities:
 *
 * <ul>
 *   <li><b>BeforeEach</b> — initialises {@link TestContext}, sets MDC fields
 *       (testId, testClass, traceId), opens an OTel span.</li>
 *   <li><b>AfterEach</b> — captures outcome, closes the span, publishes to
 *       the platform via {@link NativeReportPublisher}.</li>
 * </ul>
 *
 * <p>Register via {@code @ExtendWith(PlatformExtension.class)} or use
 * {@link com.platform.testframework.base.PlatformBaseTest} which pre-wires it.</p>
 */
public class PlatformExtension
        implements BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final Logger log = LoggerFactory.getLogger(PlatformExtension.class);

    // Extension-level storage (one instance per test class)
    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(PlatformExtension.class);

    private static final String KEY_START = "startMs";
    private static final String KEY_SPAN  = "otelSpan";

    @Override
    public void beforeEach(ExtensionContext ctx) {
        Method method = ctx.getRequiredTestMethod();
        Class<?> clazz = ctx.getRequiredTestClass();

        String className  = clazz.getName();
        String methodName = method.getName();
        String testId     = className + "#" + methodName;
        String displayName = ctx.getDisplayName();

        // --- OTel trace ---
        String traceId = generateTraceId(testId);
        Span span = openSpan(testId, className, methodName, ctx);
        ctx.getStore(NS).put(KEY_SPAN, span);

        // --- Config ---
        PlatformConfig config = PlatformConfig.load();

        // --- MDC ---
        MDC.put("run_id",      RunContext.getRunId());
        MDC.put("testId",      testId);
        MDC.put("testClass",   className);
        MDC.put("testMethod",  methodName);
        MDC.put("traceId",     traceId);
        MDC.put("team_id",     config.getTeamId());
        MDC.put("project_id",  config.getProjectId());
        MDC.put("hostname",    RunContext.getHostname());
        MDC.put("ci_provider", RunContext.getCiProvider());
        MDC.put("build_id",    RunContext.getBuildId());

        // --- Tags from annotations ---
        List<String> tags = collectTags(clazz, method);

        // --- TestContext ---
        TestContext testCtx = new TestContext(
                testId, displayName, className, methodName, tags,
                traceId,
                config.getTeamId(),
                config.getProjectId()
        );
        testCtx.putAllEnvironment(EnvironmentInfo.collect());

        // --- @AffectedBy — method level takes precedence over class level ---
        List<String> coveredClasses = collectCoveredClasses(clazz, method);
        if (!coveredClasses.isEmpty()) {
            testCtx.setCoveredClasses(coveredClasses);
        }
        TestContextHolder.set(testCtx);

        // --- Timing ---
        ctx.getStore(NS).put(KEY_START, Instant.now().toEpochMilli());

        log.info("[Platform] Test started — {} traceId={}", testId, traceId);
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        long startMs = ctx.getStore(NS).getOrDefault(KEY_START, Long.class, Instant.now().toEpochMilli());
        long durationMs = Instant.now().toEpochMilli() - startMs;

        TestContext testCtx = TestContextHolder.get();
        if (testCtx == null) {
            cleanupMdc();
            return;
        }

        TestContext.Snapshot snap = testCtx.snapshot();

        // Determine outcome
        TestStatus status;
        String failureMessage = null;
        String stackTrace     = null;

        if (ctx.getExecutionException().isPresent()) {
            Throwable t = ctx.getExecutionException().get();
            if (t instanceof AssertionError) {
                status = TestStatus.FAILED;
            } else {
                status = TestStatus.BROKEN;
            }
            failureMessage = t.getMessage();
            stackTrace     = stackTraceToString(t);

            // Classify failure and attach hint to context for the platform AI
            String lastStep = snap.rootSteps().isEmpty() ? null
                    : snap.rootSteps().get(snap.rootSteps().size() - 1).getName();
            FailureHint hint = FailureClassifier.classify(t, lastStep, snap.capturedLog());
            testCtx.putEnvironment("platform.hint.category",    hint.category().name());
            testCtx.putEnvironment("platform.hint.confidence",  String.format("%.2f", hint.confidence()));
            testCtx.putEnvironment("platform.hint.message",     hint.message());

            // AI locator analysis — triggered for BAD_LOCATOR failures when a
            // DiagnosticsProvider is registered on this thread (e.g. from BaseTest).
            if (hint.category() == FailureCategory.BAD_LOCATOR) {
                runAiLocatorAnalysis(t, testCtx);
            }
        } else {
            status = TestStatus.PASSED;
        }

        // Close OTel span
        Span span = ctx.getStore(NS).get(KEY_SPAN, Span.class);
        if (span != null) {
            span.setAttribute("test.status", status.name());
            span.end();
        }

        log.info("[Platform] Test finished — {} status={} duration={}ms traceId={}",
                snap.testId(), status, durationMs, snap.traceId());

        // Publish to platform (non-fatal)
        try {
            PlatformConfig config = PlatformConfig.load();
            NativeReportPublisher publisher = new NativeReportPublisher(config);
            publisher.publish(snap, status, failureMessage, stackTrace, durationMs);
        } catch (Exception e) {
            log.warn("[Platform] Failed to publish test result (non-fatal): {}", e.getMessage());
        }

        // Cleanup
        TestContextHolder.clear();
        cleanupMdc();
    }

    // TestWatcher callbacks — for IDE/report integration
    @Override
    public void testSuccessful(ExtensionContext ctx) {}

    @Override
    public void testFailed(ExtensionContext ctx, Throwable cause) {}

    @Override
    public void testDisabled(ExtensionContext ctx, java.util.Optional<String> reason) {
        // Mark skipped but still publish (skipped tests are valuable signal)
        TestContext testCtx = TestContextHolder.get();
        if (testCtx != null) {
            TestContext.Snapshot snap = testCtx.snapshot();
            try {
                PlatformConfig config = PlatformConfig.load();
                new NativeReportPublisher(config).publish(snap, TestStatus.SKIPPED, reason.orElse(null), null, 0);
            } catch (Exception e) {
                log.warn("[Platform] Could not publish skipped test: {}", e.getMessage());
            }
        }
        TestContextHolder.clear();
        cleanupMdc();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String generateTraceId(String testId) {
        // Use OTel current span if available (e.g. test runs inside a parent trace),
        // otherwise generate a deterministic UUID from the test ID.
        try {
            Span current = Span.current();
            String otelTraceId = current.getSpanContext().getTraceId();
            if (!otelTraceId.equals("00000000000000000000000000000000")) {
                return otelTraceId;
            }
        } catch (Exception ignored) {}
        // Fallback: random UUID
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Span openSpan(String testId, String className, String methodName, ExtensionContext ctx) {
        try {
            Tracer tracer = GlobalOpenTelemetry.getTracer("platform-testkit-java");
            return tracer.spanBuilder(testId)
                    .setAttribute("test.class",  className)
                    .setAttribute("test.method", methodName)
                    .setAttribute("test.displayName", ctx.getDisplayName())
                    .startSpan();
        } catch (Exception e) {
            return Span.getInvalid();
        }
    }

    private List<String> collectTags(Class<?> clazz, Method method) {
        List<String> tags = new ArrayList<>();
        // JUnit @Tag annotations
        for (var ann : method.getAnnotationsByType(org.junit.jupiter.api.Tag.class)) {
            tags.add(ann.value());
        }
        for (var ann : clazz.getAnnotationsByType(org.junit.jupiter.api.Tag.class)) {
            tags.add(ann.value());
        }
        // @TestMetadata fields
        TestMetadata meta = method.getAnnotation(TestMetadata.class);
        if (meta == null) meta = clazz.getAnnotation(TestMetadata.class);
        if (meta != null) {
            if (!meta.feature().isBlank()) tags.add("feature:" + meta.feature());
            if (!meta.story().isBlank())   tags.add("story:" + meta.story());
            if (!meta.owner().isBlank())   tags.add("owner:" + meta.owner());
            tags.add("severity:" + meta.severity().name().toLowerCase());
        }
        return tags;
    }

    private List<String> collectCoveredClasses(Class<?> clazz, Method method) {
        AffectedBy methodAnn = method.getAnnotation(AffectedBy.class);
        if (methodAnn != null) return List.of(methodAnn.value());
        AffectedBy classAnn = clazz.getAnnotation(AffectedBy.class);
        if (classAnn != null) return List.of(classAnn.value());
        return List.of();
    }

    private void cleanupMdc() {
        MDC.remove("run_id");
        MDC.remove("testId");
        MDC.remove("testClass");
        MDC.remove("testMethod");
        MDC.remove("traceId");
        MDC.remove("team_id");
        MDC.remove("project_id");
        MDC.remove("hostname");
        MDC.remove("ci_provider");
        MDC.remove("build_id");
        MDC.remove("step");
    }

    private void runAiLocatorAnalysis(Throwable t, TestContext ctx) {
        DiagnosticsRegistry.get().ifPresentOrElse(provider -> {
            String selector = extractSelector(t.getMessage());
            LocatorAiAnalyzer.attach(selector, provider, ctx);
            log.info("[Diagnostics] Diagnostic data attached — platform-ai will classify on the backend");
        }, () -> log.debug("[Diagnostics] No DiagnosticsProvider registered — skipping"));
    }

    private String extractSelector(String msg) {
        if (msg == null) return "(unknown)";
        for (String prefix : List.of("By.cssSelector:", "By.xpath:", "By.id:",
                                     "By.name:", "By.className:")) {
            int idx = msg.indexOf(prefix);
            if (idx >= 0) {
                String rest = msg.substring(idx + prefix.length()).trim();
                int end = rest.indexOf('\n');
                return prefix.trim().replace(":", "=") + (end > 0 ? rest.substring(0, end).trim() : rest.trim());
            }
        }
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl).trim() : msg.trim();
    }

    private String stackTraceToString(Throwable t) {
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
