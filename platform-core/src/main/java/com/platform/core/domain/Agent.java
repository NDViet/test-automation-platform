package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A reusable AI agent: a named bundle of persona + referenced prompt templates + skills + model +
 * injected-context config, owned at ORG or PROJECT scope. Agents compose by reference — {@code
 * systemTemplateId}/{@code userTemplateId} point at {@link AiPromptTemplate} rows and {@code
 * skillIdsJson} is a JSON array of {@link AiSkill} ids — so edits to those propagate.
 *
 * <p>Built-in default ("seed") agents are not persisted here; they are code fallbacks in the
 * resolution service. This table holds only user-authored org/project agents.
 */
@Entity
@Table(name = "agents")
public class Agent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** ORG | PROJECT */
  @Column(name = "scope", nullable = false, length = 10)
  private String scope;

  /**
   * organizations.id when scope=ORG, projects.id when scope=PROJECT (polymorphic, app-validated).
   */
  @Column(name = "scope_id", nullable = false)
  private UUID scopeId;

  @Column(name = "name", nullable = false, columnDefinition = "TEXT")
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "persona", columnDefinition = "TEXT")
  private String persona;

  @Column(name = "system_template_id")
  private UUID systemTemplateId;

  @Column(name = "user_template_id")
  private UUID userTemplateId;

  /** JSON array of AiSkill UUIDs, applied in order. */
  @Column(name = "skill_ids", columnDefinition = "TEXT")
  private String skillIdsJson;

  /** STANDARD | COMPLEX | SUMMARIZER — resolves to the configured model id. */
  @Column(name = "model_role", length = 20)
  private String modelRole;

  /** Explicit LiteLLM model id; overrides {@link #modelRole} when set. */
  @Column(name = "model_id", columnDefinition = "TEXT")
  private String modelId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "context_config", columnDefinition = "jsonb")
  private Map<String, Object> contextConfig;

  @Column(name = "max_rounds", nullable = false)
  private int maxRounds = 3;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "created_by", length = 200)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Agent() {}

  public Agent(
      String scope,
      UUID scopeId,
      String name,
      String description,
      String persona,
      UUID systemTemplateId,
      UUID userTemplateId,
      String skillIdsJson,
      String modelRole,
      String modelId,
      Map<String, Object> contextConfig,
      int maxRounds,
      boolean enabled,
      String createdBy) {
    this.scope = scope;
    this.scopeId = scopeId;
    this.name = name;
    this.description = description;
    this.persona = persona;
    this.systemTemplateId = systemTemplateId;
    this.userTemplateId = userTemplateId;
    this.skillIdsJson = skillIdsJson;
    this.modelRole = modelRole;
    this.modelId = modelId;
    this.contextConfig = contextConfig;
    this.maxRounds = maxRounds;
    this.enabled = enabled;
    this.createdBy = createdBy;
  }

  /** Apply an edit and bump {@code updatedAt}. Scope/owner are immutable after creation. */
  public void update(
      String name,
      String description,
      String persona,
      UUID systemTemplateId,
      UUID userTemplateId,
      String skillIdsJson,
      String modelRole,
      String modelId,
      Map<String, Object> contextConfig,
      int maxRounds,
      boolean enabled) {
    this.name = name;
    this.description = description;
    this.persona = persona;
    this.systemTemplateId = systemTemplateId;
    this.userTemplateId = userTemplateId;
    this.skillIdsJson = skillIdsJson;
    this.modelRole = modelRole;
    this.modelId = modelId;
    this.contextConfig = contextConfig;
    this.maxRounds = maxRounds;
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

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getPersona() {
    return persona;
  }

  public UUID getSystemTemplateId() {
    return systemTemplateId;
  }

  public UUID getUserTemplateId() {
    return userTemplateId;
  }

  public String getSkillIdsJson() {
    return skillIdsJson;
  }

  public String getModelRole() {
    return modelRole;
  }

  public String getModelId() {
    return modelId;
  }

  public Map<String, Object> getContextConfig() {
    return contextConfig;
  }

  public int getMaxRounds() {
    return maxRounds;
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
    if (!(o instanceof Agent a)) return false;
    return Objects.equals(id, a.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
