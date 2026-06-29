package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A reusable, project-scoped instruction set the user can attach to an AI test-case generation run
 * to steer how the agent generates. The {@code instructions} body is appended to the resolved
 * system prompt at generation time.
 */
@Entity
@Table(name = "ai_skills")
public class AiSkill {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id")
  private UUID projectId;

  /** ORG | PROJECT — owning scope (see V2). Defaults to PROJECT for the existing CRUD path. */
  @Column(name = "scope", nullable = false, length = 10)
  private String scope = "PROJECT";

  /** organizations.id or projects.id depending on {@link #scope}. */
  @Column(name = "scope_id", nullable = false)
  private UUID scopeId;

  @Column(name = "name", nullable = false, columnDefinition = "TEXT")
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "instructions", nullable = false, columnDefinition = "TEXT")
  private String instructions;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "created_by", length = 200)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected AiSkill() {}

  public AiSkill(
      UUID projectId,
      String name,
      String description,
      String instructions,
      boolean enabled,
      String createdBy) {
    this.projectId = projectId;
    this.scope = "PROJECT";
    this.scopeId = projectId;
    this.name = name;
    this.description = description;
    this.instructions = instructions;
    this.enabled = enabled;
    this.createdBy = createdBy;
  }

  /** Apply an edit and bump {@code updatedAt}. */
  public void update(String name, String description, String instructions, boolean enabled) {
    this.name = name;
    this.description = description;
    this.instructions = instructions;
    this.enabled = enabled;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getScope() {
    return scope;
  }

  public UUID getScopeId() {
    return scopeId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getInstructions() {
    return instructions;
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
    if (!(o instanceof AiSkill s)) return false;
    return Objects.equals(id, s.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
