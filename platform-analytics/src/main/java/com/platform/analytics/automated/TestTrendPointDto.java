package com.platform.analytics.automated;

public record TestTrendPointDto(
    /** UTC date in YYYY-MM-DD format. */
    String date,
    int total,
    int passed,
    int failed,
    int skipped,
    double passRate,
    /** null when no runs on this day. */
    Long avgDurationMs) {}
