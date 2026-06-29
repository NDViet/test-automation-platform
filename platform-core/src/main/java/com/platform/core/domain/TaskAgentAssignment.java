package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Binds an {@link Agent} as the default for a (task type, sub-type) at a given scope. Resolution at
 * execution time is: explicit selection → PROJECT assignment → ORG assignment → built-in seed.
 * {@code subType} is {@code DEFAULT} for tasks that aren't sub-typed.
 */
@Entity
@Table(name = "task_agent_assignments")
public class TaskAgentAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** ORG | PROJECT */
  @Column(name = "scope", nullable = false, length = 10)
  private String scope;

  @Column(name = "scope_id", nullable = false)
  private UUID scopeId;

  /** AgentTaskType name. */
  @Column(name = "task_type", nullable = false, length = 60)
  private String taskType;

  @Column(name = "sub_type", nullable = false, length = 40)
  private String subType = "DEFAULT";

  @Column(name = "agent_id", nullable = false)
  private UUID agentId;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "created_by", length = 200)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected TaskAgentAssignment() {}

  public TaskAgentAssignment(
      String scope,
      UUID scopeId,
      String taskType,
      String subType,
      UUID agentId,
      boolean enabled,
      String createdBy) {
    this.scope = scope;
    this.scopeId = scopeId;
    this.taskType = taskType;
    this.subType = subType;
    this.agentId = agentId;
    this.enabled = enabled;
    this.createdBy = createdBy;
  }

  public void reassign(UUID agentId, boolean enabled) {
    this.agentId = agentId;
    this.enabled = enabled;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getScope() {
    return scope;
  }

  public UUID getScopeId() {
    return scopeId;
  }

  public String getTaskType() {
    return taskType;
  }

  public String getSubType() {
    return subType;
  }

  public UUID getAgentId() {
    return agentId;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TaskAgentAssignment a)) return false;
    return Objects.equals(id, a.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
