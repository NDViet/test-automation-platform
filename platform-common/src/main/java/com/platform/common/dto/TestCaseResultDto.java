package com.platform.common.dto;

import com.platform.common.enums.TestStatus;

import java.util.List;
import java.util.Map;

/**
 * Normalized test case result — the per-test unit of the platform.
 *
 * <p>All parsers (JUnit XML, Cucumber, TestNG, Allure, Playwright, Newman)
 * and the platform-testframework produce this record. Use {@link #basic} for
 * parser-generated results (no step/trace data); use the canonical constructor
 * when publishing from the test framework with full debugging context.</p>
 */
public record TestCaseResultDto(
        String testId,               // fully qualified: com.example.MyTest#myMethod
        String displayName,
        String className,
        String methodName,
        List<String> tags,           // maps to features, stories, epics
        TestStatus status,
        Long durationMs,
        String failureMessage,
        String stackTrace,
        int retryCount,
        List<String> attachments,    // screenshot/log file paths

        // Rich fields populated by platform-testframework (null-safe for parsers)
        List<TestStepDto> steps,          // ordered step log with timing
        String traceId,                   // OTel trace ID for correlating logs/traces
        Map<String, String> environment,  // OS, JVM, browser, APP_URL, etc.

        // Test Impact Analysis — which production classes/modules this test covers
        // Populated from @AffectedBy (Java) or coveredModules (JS). Empty for parser-ingested results.
        List<String> coveredClasses
) {
    public TestCaseResultDto {
        if (tags == null)           tags           = List.of();
        if (attachments == null)    attachments    = List.of();
        if (steps == null)          steps          = List.of();
        if (environment == null)    environment    = Map.of();
        if (coveredClasses == null) coveredClasses = List.of();
    }

    /**
     * Factory for parser-produced results — no step/trace data.
     * All existing parsers (JUnit XML, Cucumber, TestNG, Allure, Playwright, Newman)
     * should use this instead of the canonical constructor.
     */
    public static TestCaseResultDto basic(
            String testId, String displayName, String className, String methodName,
            List<String> tags, TestStatus status, Long durationMs,
            String failureMessage, String stackTrace, int retryCount, List<String> attachments) {
        return new TestCaseResultDto(
                testId, displayName, className, methodName,
                tags, status, durationMs, failureMessage, stackTrace, retryCount, attachments,
                List.of(), null, Map.of(), List.of());
    }
}
