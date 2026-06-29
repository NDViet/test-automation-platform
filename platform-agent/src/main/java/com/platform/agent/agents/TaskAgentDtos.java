package com.platform.agent.agents;

import java.util.UUID;

/** Small DTOs/payloads for task→agent assignment management. */
public final class TaskAgentDtos {

  private TaskAgentDtos() {}

  /** Create/upsert payload: which agent is the default for (taskType, subType) at a scope. */
  public record TaskAgentRequest(String taskType, String subType, UUID agentId) {
    public String subTypeOrDefault() {
      return subType == null || subType.isBlank() ? "DEFAULT" : subType.trim();
    }
  }

  public record TaskAgentDto(
      UUID id,
      String scope,
      UUID scopeId,
      String taskType,
      String subType,
      UUID agentId,
      boolean enabled) {}

  public record TaskSubTypeDto(String taskType, String key, String label, boolean isDefault) {}

  /** Resolved default for a (project, task, subType): where it came from + which agent. */
  public record EffectiveAssignmentDto(String source, UUID agentId, String agentName) {}
}
