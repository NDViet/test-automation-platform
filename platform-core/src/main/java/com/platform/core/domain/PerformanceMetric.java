package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregated performance metrics for a single test execution produced by
 * K6, Gatling, or JMeter.
 *
 * <p>One row per {@link TestExecution} — enforced by the unique constraint on
 * {@code execution_id}.</p>
 */
@Entity
@Table(name = "performance_metrics")
@EntityListeners(AuditingEntityListener.class)
public class PerformanceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false, unique = true)
    private TestExecution execution;

    // ── Response-time distribution (ms) ──────────────────────────────────────
    @Column(name = "avg_ms")    private Double avgMs;
    @Column(name = "min_ms")    private Double minMs;
    @Column(name = "median_ms") private Double medianMs;
    @Column(name = "max_ms")    private Double maxMs;
    @Column(name = "p90_ms")    private Double p90Ms;
    @Column(name = "p95_ms")    private Double p95Ms;
    @Column(name = "p99_ms")    private Double p99Ms;

    // ── Throughput ────────────────────────────────────────────────────────────
    @Column(name = "requests_total")        private Long   requestsTotal;
    @Column(name = "requests_per_second")   private Double requestsPerSecond;

    // ── Reliability ───────────────────────────────────────────────────────────
    @Column(name = "error_rate")  private Double errorRate;

    // ── Concurrency ───────────────────────────────────────────────────────────
    @Column(name = "vus_max")     private Integer vusMax;

    // ── Duration ──────────────────────────────────────────────────────────────
    @Column(name = "duration_ms") private Long durationMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PerformanceMetric() {}

    public PerformanceMetric(TestExecution execution,
                              Double avgMs, Double minMs, Double medianMs, Double maxMs,
                              Double p90Ms, Double p95Ms, Double p99Ms,
                              Long requestsTotal, Double requestsPerSecond,
                              Double errorRate, Integer vusMax, Long durationMs) {
        this.execution         = execution;
        this.avgMs             = avgMs;
        this.minMs             = minMs;
        this.medianMs          = medianMs;
        this.maxMs             = maxMs;
        this.p90Ms             = p90Ms;
        this.p95Ms             = p95Ms;
        this.p99Ms             = p99Ms;
        this.requestsTotal     = requestsTotal;
        this.requestsPerSecond = requestsPerSecond;
        this.errorRate         = errorRate;
        this.vusMax            = vusMax;
        this.durationMs        = durationMs;
    }

    // Getters
    public UUID    getId()                { return id; }
    public TestExecution getExecution()   { return execution; }
    public Double  getAvgMs()             { return avgMs; }
    public Double  getMinMs()             { return minMs; }
    public Double  getMedianMs()          { return medianMs; }
    public Double  getMaxMs()             { return maxMs; }
    public Double  getP90Ms()             { return p90Ms; }
    public Double  getP95Ms()             { return p95Ms; }
    public Double  getP99Ms()             { return p99Ms; }
    public Long    getRequestsTotal()     { return requestsTotal; }
    public Double  getRequestsPerSecond() { return requestsPerSecond; }
    public Double  getErrorRate()         { return errorRate; }
    public Integer getVusMax()            { return vusMax; }
    public Long    getDurationMs()        { return durationMs; }
    public Instant getCreatedAt()         { return createdAt; }
}
