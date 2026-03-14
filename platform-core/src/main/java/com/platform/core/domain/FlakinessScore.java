package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "flakiness_scores",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_flakiness_test_project",
           columnNames = {"test_id", "project_id"}))
@EntityListeners(AuditingEntityListener.class)
public class FlakinessScore {

    public enum Classification { STABLE, WATCH, FLAKY, CRITICAL_FLAKY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "test_id", nullable = false, length = 500)
    private String testId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "score", nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 20)
    private Classification classification;

    @Column(name = "total_runs", nullable = false)
    private int totalRuns;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "failure_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal failureRate;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "last_passed_at")
    private Instant lastPassedAt;

    @LastModifiedDate
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected FlakinessScore() {}

    private FlakinessScore(Builder b) {
        this.testId = b.testId;
        this.projectId = b.projectId;
        this.score = b.score;
        this.classification = b.classification;
        this.totalRuns = b.totalRuns;
        this.failureCount = b.failureCount;
        this.failureRate = b.failureRate;
        this.lastFailedAt = b.lastFailedAt;
        this.lastPassedAt = b.lastPassedAt;
    }

    public static Builder builder() { return new Builder(); }

    // Getters
    public UUID getId() { return id; }
    public String getTestId() { return testId; }
    public UUID getProjectId() { return projectId; }
    public BigDecimal getScore() { return score; }
    public Classification getClassification() { return classification; }
    public int getTotalRuns() { return totalRuns; }
    public int getFailureCount() { return failureCount; }
    public BigDecimal getFailureRate() { return failureRate; }
    public Instant getLastFailedAt() { return lastFailedAt; }
    public Instant getLastPassedAt() { return lastPassedAt; }
    public Instant getComputedAt() { return computedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlakinessScore f)) return false;
        return Objects.equals(id, f.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static class Builder {
        private String testId;
        private UUID projectId;
        private BigDecimal score;
        private Classification classification;
        private int totalRuns;
        private int failureCount;
        private BigDecimal failureRate;
        private Instant lastFailedAt;
        private Instant lastPassedAt;

        public Builder testId(String v) { this.testId = v; return this; }
        public Builder projectId(UUID v) { this.projectId = v; return this; }
        public Builder score(BigDecimal v) { this.score = v; return this; }
        public Builder classification(Classification v) { this.classification = v; return this; }
        public Builder totalRuns(int v) { this.totalRuns = v; return this; }
        public Builder failureCount(int v) { this.failureCount = v; return this; }
        public Builder failureRate(BigDecimal v) { this.failureRate = v; return this; }
        public Builder lastFailedAt(Instant v) { this.lastFailedAt = v; return this; }
        public Builder lastPassedAt(Instant v) { this.lastPassedAt = v; return this; }
        public FlakinessScore build() { return new FlakinessScore(this); }
    }
}
