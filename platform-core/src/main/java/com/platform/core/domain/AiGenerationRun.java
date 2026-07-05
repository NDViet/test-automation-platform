package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The resolved inputs for a single AI test-case generation workflow (1:1 with {@code
 * agent_workflows}). Captures what the user supplied (skills, free text, file manifest, per-run
 * prompt overrides) and what was actually sent to the model ({@code systemPromptUsed}/{@code
 * userPromptUsed}) for audit and reproducibility.
 */
@Entity
@Table(name = "ai_generation_runs")
public class AiGenerationRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "workflow_id", nullable = false, unique = true)
  private UUID workflowId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  /** JSON array of selected skill UUIDs. */
  @Column(name = "skill_ids", columnDefinition = "TEXT")
  private String skillIdsJson;

  @Column(name = "free_text", columnDefinition = "TEXT")
  private String freeText;

  /** JSON array describing attached input files (id, name). */
  @Column(name = "attachment_manifest", columnDefinition = "TEXT")
  private String attachmentManifestJson;

  @Column(name = "system_prompt_override", columnDefinition = "TEXT")
  private String systemPromptOverride;

  @Column(name = "user_prompt_override", columnDefinition = "TEXT")
  private String userPromptOverride;

  /** Exact resolved prompts sent to the model (filled during assembly). */
  @Column(name = "system_prompt_used", columnDefinition = "TEXT")
  private String systemPromptUsed;

  @Column(name = "user_prompt_used", columnDefinition = "TEXT")
  private String userPromptUsed;

  @Column(name = "max_rounds", nullable = false)
  private int maxRounds = 3;

  /** The agent resolved for this run (null ⇒ built-in seed). */
  @Column(name = "agent_id")
  private UUID agentId;

  /** The task sub-type chosen for this run (e.g. FUNCTIONAL / NON_FUNCTIONAL / DEFAULT). */
  @Column(name = "task_sub_type", length = 40)
  private String taskSubType;

  /** Agent's explicit model id override, if any (consumed by the node). */
  @Column(name = "resolved_model_id", columnDefinition = "TEXT")
  private String resolvedModelId;

  /**
   * Conversation checkpoint captured when the run finished, so proposals can be refined by resuming
   * the same conversation. Updated after each refinement round.
   */
  @Column(name = "review_checkpoint_id", columnDefinition = "TEXT")
  private String reviewCheckpointId;

  /**
   * The selected Impact-Analysis / PR context injected into the latest regeneration round (JSON),
   * for audit. Null until the run is regenerated with PR context from the Review Queue.
   */
  @Column(name = "pr_context_json", columnDefinition = "TEXT")
  private String prContextJson;

  /**
   * The effective requirement id set (JSON array) for the latest regeneration round. Persisted on
   * the run so "add more requirements" does not mutate the shared workflow trigger. Null ⇒ use the
   * workflow trigger's original requirement set.
   */
  @Column(name = "requirement_ids_json", columnDefinition = "TEXT")
  private String requirementIdsJson;

  /** Number of regeneration rounds run from the Review Queue (0 for the original generation). */
  @Column(name = "regen_round", nullable = false)
  private int regenRound = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected AiGenerationRun() {}

  public AiGenerationRun(
      UUID workflowId,
      UUID projectId,
      String skillIdsJson,
      String freeText,
      String attachmentManifestJson,
      String systemPromptOverride,
      String userPromptOverride,
      int maxRounds) {
    this.workflowId = workflowId;
    this.projectId = projectId;
    this.skillIdsJson = skillIdsJson;
    this.freeText = freeText;
    this.attachmentManifestJson = attachmentManifestJson;
    this.systemPromptOverride = systemPromptOverride;
    this.userPromptOverride = userPromptOverride;
    this.maxRounds = maxRounds;
  }

  public void recordResolvedPrompts(String systemPromptUsed, String userPromptUsed) {
    this.systemPromptUsed = systemPromptUsed;
    this.userPromptUsed = userPromptUsed;
  }

  /** Save the conversation checkpoint to resume from when refining the run's proposals. */
  public void recordReviewCheckpoint(String reviewCheckpointId) {
    this.reviewCheckpointId = reviewCheckpointId;
  }

  public String getReviewCheckpointId() {
    return reviewCheckpointId;
  }

  /**
   * Persist the effective requirement id set for this run (JSON array) without counting it as a
   * regeneration. Called at initial generation so the requirement set survives the workflow parking
   * (the trigger's entityExternalId is not reconstructed on resume) and can drive PR-context
   * resolution + regeneration.
   */
  public void recordRequirementSet(String requirementIdsJson) {
    this.requirementIdsJson = requirementIdsJson;
  }

  /**
   * Record the augmented inputs for a fresh regeneration round from the Review Queue and increment
   * the regeneration counter. Any argument may be null to leave the corresponding field cleared.
   */
  public void recordRegenInputs(String requirementIdsJson, String freeText, String prContextJson) {
    this.requirementIdsJson = requirementIdsJson;
    this.freeText = freeText;
    this.prContextJson = prContextJson;
    this.regenRound += 1;
  }

  public String getPrContextJson() {
    return prContextJson;
  }

  public String getRequirementIdsJson() {
    return requirementIdsJson;
  }

  public int getRegenRound() {
    return regenRound;
  }

  /** Record which agent (and sub-type / model) drove this run, for audit and node consumption. */
  public void recordAgentResolution(UUID agentId, String taskSubType, String resolvedModelId) {
    this.agentId = agentId;
    this.taskSubType = taskSubType;
    this.resolvedModelId = resolvedModelId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkflowId() {
    return workflowId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getSkillIdsJson() {
    return skillIdsJson;
  }

  public String getFreeText() {
    return freeText;
  }

  public String getAttachmentManifestJson() {
    return attachmentManifestJson;
  }

  public String getSystemPromptOverride() {
    return systemPromptOverride;
  }

  public String getUserPromptOverride() {
    return userPromptOverride;
  }

  public String getSystemPromptUsed() {
    return systemPromptUsed;
  }

  public String getUserPromptUsed() {
    return userPromptUsed;
  }

  public int getMaxRounds() {
    return maxRounds;
  }

  public UUID getAgentId() {
    return agentId;
  }

  public String getTaskSubType() {
    return taskSubType;
  }

  public String getResolvedModelId() {
    return resolvedModelId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AiGenerationRun r)) return false;
    return Objects.equals(id, r.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
