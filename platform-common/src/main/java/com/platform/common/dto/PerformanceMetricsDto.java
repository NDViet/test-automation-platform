package com.platform.common.dto;

/**
 * Performance signals extracted from load-test runners (K6, Gatling, JMeter).
 *
 * <p>All time values are in <strong>milliseconds</strong>.
 * Null fields mean the metric was not available in the source report.
 *
 * <p>Carried through the pipeline in {@link UnifiedTestResult#performanceMetrics()}
 * and persisted to the {@code performance_metrics} table.
 */
public record PerformanceMetricsDto(

        // ── Response-time distribution ─────────────────────────────────────
        Double avgMs,
        Double minMs,
        Double medianMs,
        Double maxMs,
        Double p90Ms,
        Double p95Ms,
        Double p99Ms,

        // ── Throughput ─────────────────────────────────────────────────────
        Long   requestsTotal,
        Double requestsPerSecond,

        // ── Reliability ────────────────────────────────────────────────────
        /** Fraction of failed requests: 0.0 (no errors) → 1.0 (all failed). */
        Double errorRate,

        // ── Concurrency ────────────────────────────────────────────────────
        Integer vusMax,

        // ── Scenario duration ──────────────────────────────────────────────
        Long durationMs
) {
    /** Convenience factory — all unknown/unavailable fields can be passed as null. */
    public static PerformanceMetricsDto of(
            Double avgMs, Double minMs, Double medianMs, Double maxMs,
            Double p90Ms, Double p95Ms, Double p99Ms,
            Long requestsTotal, Double requestsPerSecond,
            Double errorRate, Integer vusMax, Long durationMs) {
        return new PerformanceMetricsDto(
                avgMs, minMs, medianMs, maxMs,
                p90Ms, p95Ms, p99Ms,
                requestsTotal, requestsPerSecond,
                errorRate, vusMax, durationMs);
    }
}
