package com.platform.core.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sot_test_plans")
public class SotTestPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "release_id")
  private UUID releaseId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  /** DRAFT | ACTIVE | CLOSED */
  @Column(name = "state", nullable = false, length = 20)
  private String state = "DRAFT";

  @Column(name = "coverage_score", precision = 4, scale = 3)
  private BigDecimal coverageScore;

  /** LOW | MEDIUM | HIGH | CRITICAL */
  @Column(name = "risk_level", length = 10)
  private String riskLevel;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected SotTestPlan() {}

  public SotTestPlan(UUID projectId, UUID releaseId) {
    this.projectId = projectId;
    this.releaseId = releaseId;
  }

  public void activate() {
    this.state = "ACTIVE";
    this.updatedAt = Instant.now();
  }

  public void close() {
    this.state = "CLOSED";
    this.updatedAt = Instant.now();
  }

  public void updateScore(BigDecimal score, String risk) {
    this.coverageScore = score;
    this.riskLevel = risk;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getReleaseId() {
    return releaseId;
  }

  public String getState() {
    return state;
  }

  public BigDecimal getCoverageScore() {
    return coverageScore;
  }

  public String getRiskLevel() {
    return riskLevel;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SotTestPlan p)) return false;
    return Objects.equals(id, p.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
