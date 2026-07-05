package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A named, reusable test-data set (key/value JSON) scoped to a project and optional environment.
 */
@Entity
@Table(
    name = "test_dataset",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_test_dataset",
            columnNames = {"project_id", "name", "env"}))
public class TestDataset {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "env", nullable = false, length = 100)
  private String env = "";

  @Column(name = "rows", columnDefinition = "TEXT")
  private String rows;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected TestDataset() {}

  public TestDataset(UUID projectId, String name, String env, String rows) {
    this.projectId = projectId;
    this.name = name;
    this.env = env != null ? env : "";
    this.rows = rows;
  }

  public void update(String name, String env, String rows) {
    this.name = name;
    this.env = env != null ? env : "";
    this.rows = rows;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getEnv() {
    return env;
  }

  public String getRows() {
    return rows;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
