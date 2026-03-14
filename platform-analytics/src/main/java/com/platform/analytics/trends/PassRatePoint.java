package com.platform.analytics.trends;

import java.time.LocalDate;

/**
 * Single data point for a pass-rate trend chart.
 */
public record PassRatePoint(
        LocalDate date,
        double passRate,
        int totalTests,
        int passed,
        int failed
) {}
