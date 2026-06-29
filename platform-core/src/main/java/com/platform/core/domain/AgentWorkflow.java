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
@Table(name = "agent_workflows")
@EntityListeners(AuditingEntityListener.class)
public class AgentWorkflow {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "trigger_type", nullable = false, length = 30)
  private String triggerType;

  @Column(name = "trigger_source", length = 40)
  private String triggerSource;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "trigger_ref", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> triggerRef;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "PENDING";

  @Column(name = "total_input_tokens", nullable = false)
  private int totalInputTokens;

  @Column(name = "total_output_tokens", nullable = false)
  private int totalOutputTokens;

  @Column(name = "total_cost_cents", nullable = false, precision = 10, scale = 4)
  private BigDecimal totalCostCents = BigDecimal.ZERO;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AgentWorkflow() {}

  public AgentWorkflow(
      UUID projectId, String triggerType, String triggerSource, Map<String, Object> triggerRef) {
    this.projectId = projectId;
    this.triggerType = triggerType;
    this.triggerSource = triggerSource;
    this.triggerRef = triggerRef;
  }

  public void markRunning() {
    this.status = "RUNNING";
    this.startedAt = Instant.now();
  }

  public void markCompleted() {
    this.status = "COMPLETED";
    this.completedAt = Instant.now();
  }

  public void markFailed(String msg) {
    this.status = "FAILED";
    this.errorMessage = msg;
    this.completedAt = Instant.now();
  }

  public void markAwaitingReview() {
    this.status = "AWAITING_REVIEW";
  }

  /** The agent paused to ask the user for clarification; resumes on answers. */
  public void markAwaitingInput() {
    this.status = "AWAITING_INPUT";
  }

  public void addTokens(int input, int output, BigDecimal costCents) {
    this.totalInputTokens += input;
    this.totalOutputTokens += output;
    this.totalCostCents = this.totalCostCents.add(costCents);
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public String getTriggerSource() {
    return triggerSource;
  }

  public Map<String, Object> getTriggerRef() {
    return triggerRef;
  }

  public String getStatus() {
    return status;
  }

  public int getTotalInputTokens() {
    return totalInputTokens;
  }

  public int getTotalOutputTokens() {
    return totalOutputTokens;
  }

  public BigDecimal getTotalCostCents() {
    return totalCostCents;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AgentWorkflow w)) return false;
    return Objects.equals(id, w.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
