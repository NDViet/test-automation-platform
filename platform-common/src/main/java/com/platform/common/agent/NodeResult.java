package com.platform.common.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a Node returns to the Hub after completing (or pausing) a task. Hub routes artifacts to
 * ReviewGateway and updates workflow state.
 */
public record NodeResult(
    UUID sessionId,
    UUID workflowId,
    NodeType nodeType,
    AgentTaskType taskType,
    NodeResultStatus status,

    // Outputs
    ArtifactManifest artifacts,
    String summary, // human-readable one-paragraph summary for the review UI
    String checkpointId, // non-null when status == AWAITING_REVIEW or PARTIAL

    // Cost tracking
    TokenUsage tokenUsage,

    // Failure details
    String errorCode,
    String errorMessage,
    List<String> warnings,
    Instant completedAt) {
  public NodeResult {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    if (artifacts == null) artifacts = ArtifactManifest.empty();
  }

  public boolean isSuccess() {
    return status == NodeResultStatus.COMPLETED || status == NodeResultStatus.AWAITING_REVIEW;
  }

  public boolean needsReview() {
    return status == NodeResultStatus.AWAITING_REVIEW;
  }

  public boolean hasFailed() {
    return status == NodeResultStatus.FAILED;
  }

  /** Convenience factory for a clean completion. */
  public static NodeResult completed(
      UUID sessionId,
      UUID workflowId,
      NodeType nodeType,
      AgentTaskType taskType,
      ArtifactManifest artifacts,
      String summary,
      TokenUsage tokenUsage) {
    return new NodeResult(
        sessionId,
        workflowId,
        nodeType,
        taskType,
        NodeResultStatus.COMPLETED,
        artifacts,
        summary,
        null,
        tokenUsage,
        null,
        null,
        List.of(),
        Instant.now());
  }

  /** Convenience factory for a result that pauses for human review. */
  public static NodeResult awaitingReview(
      UUID sessionId,
      UUID workflowId,
      NodeType nodeType,
      AgentTaskType taskType,
      ArtifactManifest artifacts,
      String summary,
      String checkpointId,
      TokenUsage tokenUsage) {
    return new NodeResult(
        sessionId,
        workflowId,
        nodeType,
        taskType,
        NodeResultStatus.AWAITING_REVIEW,
        artifacts,
        summary,
        checkpointId,
        tokenUsage,
        null,
        null,
        List.of(),
        Instant.now());
  }

  /** Convenience factory for a failure. */
  public static NodeResult failed(
      UUID sessionId,
      UUID workflowId,
      NodeType nodeType,
      AgentTaskType taskType,
      String errorCode,
      String errorMessage,
      TokenUsage tokenUsage) {
    return new NodeResult(
        sessionId,
        workflowId,
        nodeType,
        taskType,
        NodeResultStatus.FAILED,
        ArtifactManifest.empty(),
        null,
        null,
        tokenUsage,
        errorCode,
        errorMessage,
        List.of(),
        Instant.now());
  }
}
