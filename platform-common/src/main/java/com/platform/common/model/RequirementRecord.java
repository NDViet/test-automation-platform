package com.platform.common.model;

import com.platform.common.integration.IntegrationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform-canonical representation of a requirement, regardless of which external system it came
 * from. Immutable value object used in ContextBundle and traceability graph traversals.
 */
public record RequirementRecord(
    UUID id,
    String externalId, // JIRA key, Linear ID, etc.
    IntegrationType source,
    String title,
    String description,
    List<AcceptanceCriterion> acceptanceCriteria,
    IssueType issueType,
    String status,
    String priority,
    List<String> labels,
    UUID parentId, // null for root-level (Epic)
    String path, // "EPIC_ID.STORY_ID.SUBTASK_ID" — for fast hierarchy queries
    int depth,
    Instant syncedAt) {
  public RequirementRecord {
    acceptanceCriteria = acceptanceCriteria == null ? List.of() : acceptanceCriteria;
    labels = labels == null ? List.of() : labels;
  }

  public record AcceptanceCriterion(String ref, String text) {}

  public enum IssueType {
    EPIC,
    STORY,
    TASK,
    SUBTASK,
    DEFECT,
    SPIKE
  }
}
