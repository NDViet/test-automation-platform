package com.platform.analytics.trends;

import java.util.List;

/**
 * Result of a quality gate evaluation. A gate FAILS if any configured threshold is breached.
 */
public record QualityGateResult(
        boolean passed,
        double actualPassRate,
        int newFailures,
        List<String> violations
) {
    public static QualityGateResult pass(double passRate, int newFailures) {
        return new QualityGateResult(true, passRate, newFailures, List.of());
    }

    public static QualityGateResult fail(double passRate, int newFailures, List<String> violations) {
        return new QualityGateResult(false, passRate, newFailures, violations);
    }
}
