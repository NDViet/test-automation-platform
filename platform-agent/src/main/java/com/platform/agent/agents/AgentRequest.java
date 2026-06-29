package com.platform.agent.agents;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Create/update payload for an {@link com.platform.core.domain.Agent}. */
public record AgentRequest(
    String name,
    String description,
    String persona,
    UUID systemTemplateId,
    UUID userTemplateId,
    List<String> skillIds,
    String modelRole,
    String modelId,
    Map<String, Object> contextConfig,
    Integer maxRounds,
    Boolean enabled) {

  public boolean enabledOrDefault() {
    return enabled == null || enabled;
  }

  /** Clamp to a sane 0–5 range; null ⇒ 3. */
  public int maxRoundsOrDefault() {
    return maxRounds == null ? 3 : Math.max(0, Math.min(5, maxRounds));
  }

  public List<String> skillIdsOrEmpty() {
    return skillIds == null ? List.of() : skillIds;
  }
}
