package com.platform.analytics.alerts;

/**
 * A single configurable alert rule.
 *
 * <p>Rules are loaded from {@code application.yml} under
 * {@code platform.analytics.alerts.rules}.</p>
 */
public record AlertRule(
        String name,
        Metric metric,
        double threshold,
        Severity severity,
        boolean enabled
) {
    public enum Metric {
        /** Fire when pass rate drops below threshold (0.0–1.0). */
        PASS_RATE_BELOW,
        /** Fire when new failure count in a single run exceeds threshold. */
        NEW_FAILURES_ABOVE,
        /** Fire when total broken tests in a run exceeds threshold. */
        BROKEN_TESTS_ABOVE,
        /** Fire when number of CRITICAL_FLAKY tests in the project exceeds threshold. */
        CRITICAL_FLAKY_COUNT_ABOVE
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
}
