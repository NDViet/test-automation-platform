package com.platform.analytics.automated;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AutomatedTestSummaryDto(
        String testId,
        String displayName,
        /** className field — contains suite/describe block name for Playwright results. */
        String suiteName,
        List<String> tags,
        long totalRuns,
        long passed,
        long failed,
        long skipped,
        long broken,
        double passRate,
        double failRate,
        String lastStatus,
        String lastRunId,
        Instant lastRunAt,
        double avgDurationMs,

        // ── Rich metadata (populated from test_case_results tier-1/2 columns) ──

        /** Spec file path relative to the project root, e.g. "tests/checkout/payment.spec.ts". Null for tests ingested before V13. */
        String specFile,

        /** Distinct Playwright project names (browsers/devices) seen across all results in the window. */
        List<String> browsers,

        /**
         * Distinct Playwright annotation types seen across all results (e.g. "fixme", "slow", "fail").
         * Excludes tag/tia:/label: namespaced annotations.
         */
        List<String> annotationTypes,

        /**
         * Per-label-key, the distinct values seen across all results in the window.
         * e.g. {"owner": ["alice", "bob"], "jira": ["PROJ-123"]}
         */
        Map<String, List<String>> labelMap,

        /** True if any result in the window has a screenshot attachment. */
        boolean hasScreenshot,

        /** True if any result in the window has a video attachment. */
        boolean hasVideo
) {}
