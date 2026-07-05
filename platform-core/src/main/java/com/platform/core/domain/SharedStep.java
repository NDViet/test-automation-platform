package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A reusable test step. Case steps reference it; edits here propagate to every referencing case.
 */
@Entity
@Table(name = "shared_step")
public class SharedStep {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "action", nullable = false, columnDefinition = "TEXT")
  private String action;

  @Column(name = "expected_result", columnDefinition = "TEXT")
  private String expectedResult;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected SharedStep() {}

  public SharedStep(UUID projectId, String action, String expectedResult, String notes) {
    this.projectId = projectId;
    this.action = action;
    this.expectedResult = expectedResult;
    this.notes = notes;
  }

  public void update(String action, String expectedResult, String notes) {
    this.action = action;
    this.expectedResult = expectedResult;
    this.notes = notes;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getAction() {
    return action;
  }

  public String getExpectedResult() {
    return expectedResult;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
