package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-project release quality-gate thresholds. A release is evaluated against these; the {@code
 * state -> RELEASED} transition is blocked unless the gate passes (or is overridden). One row per
 * project.
 */
@Entity
@Table(name = "quality_gate", uniqueConstraints = @UniqueConstraint(columnNames = "project_id"))
public class QualityGate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  /** Minimum acceptable pass rate, 0..1. */
  @Column(name = "min_pass_rate", nullable = false)
  private double minPassRate = 0.90;

  /** Minimum acceptable automation coverage, 0..100. */
  @Column(name = "min_coverage_pct", nullable = false)
  private double minCoveragePct = 0;

  @Column(name = "max_open_blockers", nullable = false)
  private int maxOpenBlockers = 0;

  @Column(name = "max_critical_flaky", nullable = false)
  private int maxCriticalFlaky = 0;

  @Column(name = "require_suites_executed", nullable = false)
  private boolean requireSuitesExecuted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected QualityGate() {}

  public QualityGate(UUID projectId) {
    this.projectId = projectId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public double getMinPassRate() {
    return minPassRate;
  }

  public double getMinCoveragePct() {
    return minCoveragePct;
  }

  public int getMaxOpenBlockers() {
    return maxOpenBlockers;
  }

  public int getMaxCriticalFlaky() {
    return maxCriticalFlaky;
  }

  public boolean isRequireSuitesExecuted() {
    return requireSuitesExecuted;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(
      boolean enabled,
      double minPassRate,
      double minCoveragePct,
      int maxOpenBlockers,
      int maxCriticalFlaky,
      boolean requireSuitesExecuted) {
    this.enabled = enabled;
    this.minPassRate = minPassRate;
    this.minCoveragePct = minCoveragePct;
    this.maxOpenBlockers = maxOpenBlockers;
    this.maxCriticalFlaky = maxCriticalFlaky;
    this.requireSuitesExecuted = requireSuitesExecuted;
    this.updatedAt = Instant.now();
  }
}
