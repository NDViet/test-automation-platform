package com.platform.agent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.Agent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API view of an {@link Agent}. {@code inherited} marks an org agent surfaced in a project list.
 */
public record AgentDto(
    UUID id,
    String scope,
    UUID scopeId,
    String name,
    String description,
    String persona,
    UUID systemTemplateId,
    UUID userTemplateId,
    List<String> skillIds,
    String modelRole,
    String modelId,
    Map<String, Object> contextConfig,
    int maxRounds,
    boolean enabled,
    boolean inherited,
    String createdBy,
    Instant createdAt,
    Instant updatedAt) {

  public static AgentDto from(Agent a, ObjectMapper mapper) {
    return build(a, mapper, false);
  }

  public static AgentDto inherited(Agent a, ObjectMapper mapper) {
    return build(a, mapper, true);
  }

  private static AgentDto build(Agent a, ObjectMapper mapper, boolean inherited) {
    return new AgentDto(
        a.getId(),
        a.getScope(),
        a.getScopeId(),
        a.getName(),
        a.getDescription(),
        a.getPersona(),
        a.getSystemTemplateId(),
        a.getUserTemplateId(),
        parseIds(a.getSkillIdsJson(), mapper),
        a.getModelRole(),
        a.getModelId(),
        a.getContextConfig(),
        a.getMaxRounds(),
        a.isEnabled(),
        inherited,
        a.getCreatedBy(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }

  private static List<String> parseIds(String json, ObjectMapper mapper) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return List.of(mapper.readValue(json, String[].class));
    } catch (Exception e) {
      return List.of();
    }
  }
}
