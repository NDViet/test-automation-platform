package com.platform.common.dto;

import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestType;
import com.platform.common.enums.TriggerType;

import java.time.Instant;
import java.util.List;

/**
 * Normalized test result schema — the common currency of the platform.
 * Every parser (JUnit XML, Cucumber, TestNG, Allure, Playwright, Newman)
 * produces this record. All downstream services consume this record.
 */
public record UnifiedTestResult(
        String runId,
        String teamId,
        String projectId,
        String branch,
        String environment,
        String commitSha,
        TriggerType triggerType,
        String ciProvider,
        String ciRunUrl,
        Instant executedAt,

        // Execution summary (denormalized for fast dashboard queries)
        int total,
        int passed,
        int failed,
        int skipped,
        int broken,
        Long durationMs,

        SourceFormat sourceFormat,
        List<TestCaseResultDto> testCases,

        // Execution metadata — for distribution and performance analysis
        String executionMode,   // PARALLEL | SEQUENTIAL | UNKNOWN
        int    parallelism,     // thread / fork count; 0 = unknown
        String suiteName,       // top-level suite / class / feature file

        // Classification — inferred from sourceFormat if not supplied explicitly
        TestType testType,

        // Non-null only for PERFORMANCE test types (K6, Gatling, JMeter)
        PerformanceMetricsDto performanceMetrics
) {
    public UnifiedTestResult {
        if (testCases == null)     testCases    = List.of();
        if (environment == null)   environment  = "unknown";
        if (branch == null)        branch       = "unknown";
        if (executionMode == null) executionMode = "UNKNOWN";
        if (suiteName == null)     suiteName    = "";
        if (testType == null)      testType     = TestType.from(sourceFormat);
    }
}
