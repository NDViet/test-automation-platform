package com.platform.testframework.report;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.TestStepDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.sdk.config.PlatformConfig;
import com.platform.sdk.publisher.PlatformReporter;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.step.TestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Converts accumulated {@link TestContext.Snapshot} instances into a
 * {@link UnifiedTestResult} and publishes to the platform via
 * {@link PlatformReporter#publishNative}.
 *
 * <p>Never throws — publication failures are non-fatal.</p>
 */
public final class NativeReportPublisher {

    private static final Logger log = LoggerFactory.getLogger(NativeReportPublisher.class);

    private final PlatformReporter reporter;
    private final PlatformConfig   config;

    public NativeReportPublisher(PlatformConfig config) {
        this.config   = config;
        this.reporter = new PlatformReporter(config);
    }

    /**
     * Publishes a single test result immediately after the test completes.
     * Called by {@link com.platform.testframework.extension.PlatformExtension} in {@code afterEach}.
     */
    public void publish(TestContext.Snapshot snap, TestStatus status,
                        String failureMessage, String stackTrace, long durationMs) {
        if (!config.isEnabled()) return;

        List<TestStepDto> stepDtos = snap.rootSteps().stream()
                .map(TestStep::toDto)
                .toList();

        TestCaseResultDto tc = new TestCaseResultDto(
                snap.testId(),
                snap.displayName(),
                snap.className(),
                snap.methodName(),
                snap.tags(),
                status,
                durationMs,
                failureMessage,
                stackTrace,
                snap.retryCount(),
                snap.attachments(),
                stepDtos,
                snap.traceId(),
                snap.environment(),
                snap.coveredClasses()
        );

        UnifiedTestResult result = new UnifiedTestResult(
                UUID.randomUUID().toString(),
                snap.teamId(),
                snap.projectId(),
                resolveEnv("GITHUB_REF_NAME", "CI_COMMIT_REF_NAME", "GIT_BRANCH", "unknown"),
                config.getEnvironment(),
                resolveEnv("GITHUB_SHA", "CI_COMMIT_SHA", null, null),
                null,
                EnvironmentInfo.detectCiProvider(),
                buildCiRunUrl(),
                snap.startedAt() != null ? snap.startedAt() : Instant.now(),
                1,
                status == TestStatus.PASSED ? 1 : 0,
                status == TestStatus.FAILED ? 1 : 0,
                status == TestStatus.SKIPPED ? 1 : 0,
                status == TestStatus.BROKEN ? 1 : 0,
                durationMs,
                SourceFormat.PLATFORM_NATIVE,
                List.of(tc),
                EnvironmentInfo.detectExecutionMode(),
                EnvironmentInfo.detectParallelism(),
                snap.className() != null ? snap.className() : ""
        );

        reporter.publishNative(result);
    }

    private static String resolveEnv(String key1, String key2, String key3, String fallback) {
        String v = System.getenv(key1);
        if (v != null && !v.isBlank()) return v;
        if (key2 != null) { v = System.getenv(key2); if (v != null && !v.isBlank()) return v; }
        if (key3 != null) { v = System.getenv(key3); if (v != null && !v.isBlank()) return v; }
        return fallback;
    }

    private static String buildCiRunUrl() {
        String server = System.getenv("GITHUB_SERVER_URL");
        String repo   = System.getenv("GITHUB_REPOSITORY");
        String runId  = System.getenv("GITHUB_RUN_ID");
        if (server != null && repo != null && runId != null) {
            return server + "/" + repo + "/actions/runs/" + runId;
        }
        return null;
    }
}
