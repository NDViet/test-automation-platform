package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A project-scoped, reusable prompt template for AI test-case generation. {@code kind} is SYSTEM or
 * USER. One template per (project, kind) may be marked default; the default body pre-fills the
 * generation form and is used when the run carries no per-run override.
 */
@Entity
@Table(name = "ai_prompt_templates")
public class AiPromptTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  /** SYSTEM | USER */
  @Column(name = "kind", nullable = false, length = 10)
  private String kind;

  @Column(name = "name", nullable = false, columnDefinition = "TEXT")
  private String name;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault = false;

  @Column(name = "created_by", length = 200)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected AiPromptTemplate() {}

  public AiPromptTemplate(
      UUID projectId, String kind, String name, String body, boolean isDefault, String createdBy) {
    this.projectId = projectId;
    this.kind = kind;
    this.name = name;
    this.body = body;
    this.isDefault = isDefault;
    this.createdBy = createdBy;
  }

  public void update(String name, String body, boolean isDefault) {
    this.name = name;
    this.body = body;
    this.isDefault = isDefault;
    this.updatedAt = Instant.now();
  }

  public void clearDefault() {
    this.isDefault = false;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getKind() {
    return kind;
  }

  public String getName() {
    return name;
  }

  public String getBody() {
    return body;
  }

  public boolean isDefault() {
    return isDefault;
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
    if (!(o instanceof AiPromptTemplate t)) return false;
    return Objects.equals(id, t.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
