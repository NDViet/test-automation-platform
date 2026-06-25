package com.platform.core.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "agent_workflow_steps")
@EntityListeners(AuditingEntityListener.class)
public class AgentWorkflowStep {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "workflow_id", nullable = false)
  private UUID workflowId;

  @Column(name = "node_id", nullable = false)
  private UUID nodeId;

  @Column(name = "node_type", nullable = false, length = 30)
  private String nodeType;

  @Column(name = "task_type", nullable = false, length = 50)
  private String taskType;

  @Column(name = "sequence_order", nullable = false)
  private int sequenceOrder;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "PENDING";

  @Column(name = "input_tokens", nullable = false)
  private int inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private int outputTokens;

  @Column(name = "cost_cents", nullable = false, precision = 8, scale = 4)
  private BigDecimal costCents = BigDecimal.ZERO;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "artifact_manifest", columnDefinition = "jsonb")
  private Map<String, Object> artifactManifest;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  @Column(name = "error_code", length = 50)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AgentWorkflowStep() {}

  public AgentWorkflowStep(
      UUID workflowId, UUID nodeId, String nodeType, String taskType, int sequenceOrder) {
    this.workflowId = workflowId;
    this.nodeId = nodeId;
    this.nodeType = nodeType;
    this.taskType = taskType;
    this.sequenceOrder = sequenceOrder;
  }

  public void markRunning() {
    this.status = "RUNNING";
    this.startedAt = Instant.now();
  }

  public void markCompleted(String summary, int input, int output, BigDecimal cost) {
    this.status = "COMPLETED";
    this.summary = summary;
    this.inputTokens = input;
    this.outputTokens = output;
    this.costCents = cost;
    this.completedAt = Instant.now();
  }

  public void markAwaitingReview(String summary) {
    this.status = "AWAITING_REVIEW";
    this.summary = summary;
    this.completedAt = Instant.now();
  }

  public void markFailed(String code, String msg) {
    this.status = "FAILED";
    this.errorCode = code;
    this.errorMessage = msg;
    this.completedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkflowId() {
    return workflowId;
  }

  public UUID getNodeId() {
    return nodeId;
  }

  public String getNodeType() {
    return nodeType;
  }

  public String getTaskType() {
    return taskType;
  }

  public int getSequenceOrder() {
    return sequenceOrder;
  }

  public String getStatus() {
    return status;
  }

  public int getInputTokens() {
    return inputTokens;
  }

  public int getOutputTokens() {
    return outputTokens;
  }

  public BigDecimal getCostCents() {
    return costCents;
  }

  public Map<String, Object> getArtifactManifest() {
    return artifactManifest;
  }

  public String getSummary() {
    return summary;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setArtifactManifest(Map<String, Object> m) {
    this.artifactManifest = m;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AgentWorkflowStep s)) return false;
    return Objects.equals(id, s.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
