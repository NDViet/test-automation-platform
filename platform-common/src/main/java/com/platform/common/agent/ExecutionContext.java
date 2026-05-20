package com.platform.common.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recent execution context for the Hub→Node contract.
 * Provides pass-rate trends, flakiness scores, and failure details for healing.
 */
public record ExecutionContext(
        UUID lastRunId,
        Instant lastRunAt,
        double passRate7d,           // 0.0–1.0
        double flakinessScore,       // 0.0–1.0 (from platform flakiness scorer)
        int consecutiveFailures,
        List<FailureSample> recentFailures,
        String environment
) {
    public ExecutionContext {
        recentFailures = recentFailures == null ? List.of() : List.copyOf(recentFailures);
    }

    public boolean isFlaky() { return flakinessScore >= 0.30; }

    public boolean isCriticallyFlaky() { return flakinessScore >= 0.60; }

    /**
     * A single failure sample for AI healing analysis.
     */
    public record FailureSample(
            String testCaseId,
            String testName,
            String errorMessage,
            String stackTrace,
            Instant occurredAt
    ) {}
}
