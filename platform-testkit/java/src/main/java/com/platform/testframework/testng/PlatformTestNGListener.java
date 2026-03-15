package com.platform.testframework.testng;

import com.platform.common.enums.TestStatus;
import com.platform.sdk.config.PlatformConfig;
import com.platform.testframework.classify.FailureClassifier;
import com.platform.testframework.classify.FailureHint;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.report.EnvironmentInfo;
import com.platform.testframework.report.NativeReportPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testng.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TestNG listener that integrates with platform-testkit-java.
 *
 * <p>Provides the same structured logging, OTel trace IDs, step tracking,
 * failure classification, and platform publishing as {@link
 * com.platform.testframework.extension.PlatformExtension} does for JUnit5.</p>
 *
 * <h3>Registration — choose one:</h3>
 * <pre>{@code
 * // 1. On the test class (simplest):
 * @Listeners(PlatformTestNGListener.class)
 * public class MyTest { ... }
 *
 * // 2. On a base class:
 * public abstract class BaseTest extends PlatformTestNGBase { ... }
 *
 * // 3. In testng.xml (applies to all suites):
 * <listeners>
 *   <listener class-name="com.platform.testframework.testng.PlatformTestNGListener"/>
 * </listeners>
 * }</pre>
 *
 * <h3>Step logging:</h3>
 * <pre>{@code
 * private final TestLogger log = TestLogger.forClass(getClass());
 *
 * @Test
 * public void myTest() {
 *     log.step("Navigate to login");
 *       driver.get(url);
 *     log.endStep();
 * }
 * }</pre>
 */
public class PlatformTestNGListener implements ITestListener {

    private static final Logger log = LoggerFactory.getLogger(PlatformTestNGListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        String className  = result.getTestClass().getName();
        String methodName = result.getMethod().getMethodName();
        String testId     = className + "#" + methodName;
        String displayName = result.getMethod().getDescription() != null
                && !result.getMethod().getDescription().isBlank()
                ? result.getMethod().getDescription() : methodName;

        String traceId = UUID.randomUUID().toString().replace("-", "");

        // MDC
        MDC.put("testId",    testId);
        MDC.put("testClass", className);
        MDC.put("testMethod", methodName);
        MDC.put("traceId",   traceId);

        // Tags from TestNG groups
        List<String> tags = Arrays.asList(result.getMethod().getGroups());

        PlatformConfig config = PlatformConfig.load();

        TestContext ctx = new TestContext(
                testId, displayName, className, methodName, tags,
                traceId, config.getTeamId(), config.getProjectId()
        );
        ctx.putAllEnvironment(EnvironmentInfo.collect());
        TestContextHolder.set(ctx);

        log.info("[Platform/TestNG] Test started — {} traceId={}", testId, traceId);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        publish(result, TestStatus.PASSED, null, null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        Throwable t = result.getThrowable();
        TestStatus status = (t instanceof AssertionError) ? TestStatus.FAILED : TestStatus.BROKEN;
        publish(result, status, t != null ? t.getMessage() : null, stackTrace(t));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        publish(result, TestStatus.SKIPPED, null, null);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        onTestFailure(result); // treat as failure for platform purposes
    }

    // -------------------------------------------------------------------------

    private void publish(ITestResult result, TestStatus status,
                         String failureMessage, String stackTrace) {
        TestContext ctx = TestContextHolder.get();
        if (ctx == null) {
            cleanupMdc();
            return;
        }

        long durationMs = result.getEndMillis() - result.getStartMillis();

        // Failure classification
        if (result.getThrowable() != null) {
            TestContext.Snapshot snap = ctx.snapshot(); // peek without clearing
            String lastStep = snap.rootSteps().isEmpty() ? null
                    : snap.rootSteps().get(snap.rootSteps().size() - 1).getName();
            FailureHint hint = FailureClassifier.classify(
                    result.getThrowable(), lastStep, snap.capturedLog());
            ctx.putEnvironment("platform.hint.category",    hint.category().name());
            ctx.putEnvironment("platform.hint.confidence",  String.format("%.2f", hint.confidence()));
            ctx.putEnvironment("platform.hint.message",     hint.message());
        }

        TestContext.Snapshot snap = ctx.snapshot();

        try {
            PlatformConfig config = PlatformConfig.load();
            new NativeReportPublisher(config)
                    .publish(snap, status, failureMessage, stackTrace, durationMs);
        } catch (Exception e) {
            log.warn("[Platform/TestNG] Failed to publish result: {}", e.getMessage());
        }

        log.info("[Platform/TestNG] Test finished — {} status={} duration={}ms",
                snap.testId(), status, durationMs);

        TestContextHolder.clear();
        cleanupMdc();
    }

    private void cleanupMdc() {
        MDC.remove("testId");
        MDC.remove("testClass");
        MDC.remove("testMethod");
        MDC.remove("traceId");
        MDC.remove("step");
    }

    private String stackTrace(Throwable t) {
        if (t == null) return null;
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
