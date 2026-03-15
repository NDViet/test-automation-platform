package com.platform.testframework.cucumber;

import com.platform.common.enums.TestStatus;
import com.platform.sdk.config.PlatformConfig;
import com.platform.testframework.classify.FailureClassifier;
import com.platform.testframework.classify.FailureHint;
import com.platform.testframework.context.RunContext;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.report.EnvironmentInfo;
import com.platform.testframework.report.NativeReportPublisher;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

/**
 * Cucumber plugin that integrates with platform-testkit-java.
 *
 * <p>Tracks scenario lifecycle and maps Cucumber steps to platform {@link
 * com.platform.testframework.step.TestStep}s — giving the platform per-step
 * timing and status for every BDD scenario without any test code changes.</p>
 *
 * <h3>Registration — in @CucumberOptions or cucumber.properties:</h3>
 * <pre>{@code
 * // In @CucumberOptions:
 * @CucumberOptions(plugin = {
 *     "com.platform.testframework.cucumber.PlatformCucumberPlugin"
 * })
 *
 * // Or in src/test/resources/cucumber.properties:
 * cucumber.plugin=com.platform.testframework.cucumber.PlatformCucumberPlugin
 * }</pre>
 *
 * <h3>Optional: inject TestLogger into step definitions for rich logging:</h3>
 * <pre>{@code
 * public class LoginSteps {
 *     private final TestLogger log = TestLogger.forClass(LoginSteps.class);
 *
 *     @When("the user submits the login form")
 *     public void theUserSubmitsLoginForm() {
 *         log.info("Submitting form with username={}", username);
 *         loginPage.submit();
 *     }
 * }
 * }</pre>
 */
public class PlatformCucumberPlugin implements ConcurrentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PlatformCucumberPlugin.class);

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class,    this::onTestCaseStarted);
        publisher.registerHandlerFor(TestStepStarted.class,    this::onTestStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class,   this::onTestStepFinished);
        publisher.registerHandlerFor(TestCaseFinished.class,   this::onTestCaseFinished);
    }

    // ── Scenario start ───────────────────────────────────────────────────────

    private void onTestCaseStarted(TestCaseStarted event) {
        TestCase scenario = event.getTestCase();

        String featureName  = scenario.getUri().toString();
        // Use the last segment of the URI as class name approximation
        String className    = featureName.contains("/")
                ? featureName.substring(featureName.lastIndexOf('/') + 1) : featureName;
        String scenarioName = scenario.getName();
        String testId       = className + "#" + sanitize(scenarioName);
        String traceId      = UUID.randomUUID().toString().replace("-", "");

        List<String> tags = scenario.getTags().stream()
                .map(t -> t.startsWith("@") ? t.substring(1) : t)
                .toList();

        PlatformConfig config = PlatformConfig.load();

        MDC.put("run_id",      RunContext.getRunId());
        MDC.put("testId",      testId);
        MDC.put("testClass",   className);
        MDC.put("testMethod",  scenarioName);
        MDC.put("traceId",     traceId);
        MDC.put("team_id",     config.getTeamId());
        MDC.put("project_id",  config.getProjectId());
        MDC.put("hostname",    RunContext.getHostname());
        MDC.put("ci_provider", RunContext.getCiProvider());
        MDC.put("build_id",    RunContext.getBuildId());
        TestContext ctx = new TestContext(
                testId, scenarioName, className, scenarioName, tags,
                traceId, config.getTeamId(), config.getProjectId()
        );
        ctx.putAllEnvironment(EnvironmentInfo.collect());
        TestContextHolder.set(ctx);

        log.info("[Platform/Cucumber] Scenario started — '{}' traceId={}", scenarioName, traceId);
    }

    // ── Step start ───────────────────────────────────────────────────────────

    private void onTestStepStarted(TestStepStarted event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep step)) {
            return; // skip Before/After hooks
        }
        String stepText = step.getStep().getText();
        MDC.put("step", stepText);

        TestContext ctx = TestContextHolder.get();
        if (ctx != null) {
            ctx.appendLog("[STEP] " + stepText);
            ctx.pushStep(stepText);
        }
    }

    // ── Step finish ──────────────────────────────────────────────────────────

    private void onTestStepFinished(TestStepFinished event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep)) {
            return;
        }
        MDC.remove("step");

        TestContext ctx = TestContextHolder.get();
        if (ctx == null) return;

        Result result = event.getResult();
        if (result.getStatus() == Status.FAILED) {
            // Mark the current step as failed so the step log captures it
            if (result.getError() != null) {
                ctx.appendLog("[STEP FAILED] " + result.getError().getMessage());
            }
        }
        ctx.popStep();
    }

    // ── Scenario finish ──────────────────────────────────────────────────────

    private void onTestCaseFinished(TestCaseFinished event) {
        TestContext ctx = TestContextHolder.get();
        if (ctx == null) {
            cleanupMdc();
            return;
        }

        Result result    = event.getResult();
        Throwable error  = result.getError();
        TestStatus status = mapStatus(result.getStatus());
        long durationMs  = result.getDuration().toMillis();

        // Failure classification
        if (error != null) {
            TestContext.Snapshot peek = ctx.snapshot();
            String lastStep = peek.rootSteps().isEmpty() ? null
                    : peek.rootSteps().get(peek.rootSteps().size() - 1).getName();
            FailureHint hint = FailureClassifier.classify(error, lastStep, peek.capturedLog());
            ctx.putEnvironment("platform.hint.category",    hint.category().name());
            ctx.putEnvironment("platform.hint.confidence",  String.format("%.2f", hint.confidence()));
            ctx.putEnvironment("platform.hint.message",     hint.message());
        }

        TestContext.Snapshot snap = ctx.snapshot();
        log.info("[Platform/Cucumber] Scenario finished — '{}' status={} duration={}ms",
                snap.displayName(), status, durationMs);

        try {
            PlatformConfig config = PlatformConfig.load();
            new NativeReportPublisher(config).publish(
                    snap, status,
                    error != null ? error.getMessage() : null,
                    stackTrace(error),
                    durationMs
            );
        } catch (Exception e) {
            log.warn("[Platform/Cucumber] Failed to publish result: {}", e.getMessage());
        }

        TestContextHolder.clear();
        cleanupMdc();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TestStatus mapStatus(Status cucumberStatus) {
        return switch (cucumberStatus) {
            case PASSED              -> TestStatus.PASSED;
            case FAILED              -> TestStatus.FAILED;
            case SKIPPED, PENDING    -> TestStatus.SKIPPED;
            default                  -> TestStatus.BROKEN;
        };
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

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static String stackTrace(Throwable t) {
        if (t == null) return null;
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
