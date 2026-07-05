package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A single failed execution attached to a {@link FailureCluster}. Deduped per execution. */
@Entity
@Table(
    name = "failure_occurrence",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cluster_id", "execution_id"}),
    indexes = @Index(name = "idx_failure_occurrence_cluster", columnList = "cluster_id"))
public class FailureOccurrence {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "cluster_id", nullable = false)
  private UUID clusterId;

  /** The manual TestCaseExecution id or the automated TestCaseResult id that failed. */
  @Column(name = "execution_id", nullable = false)
  private UUID executionId;

  @Column(name = "run_id")
  private UUID runId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  protected FailureOccurrence() {}

  public FailureOccurrence(UUID clusterId, UUID executionId, UUID runId) {
    this.clusterId = clusterId;
    this.executionId = executionId;
    this.runId = runId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getClusterId() {
    return clusterId;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public UUID getRunId() {
    return runId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
