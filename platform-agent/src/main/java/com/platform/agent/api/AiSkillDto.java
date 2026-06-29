package com.platform.agent.api;

import com.platform.core.domain.AiSkill;
import java.time.Instant;
import java.util.UUID;

/** API view of an {@link AiSkill}. */
public record AiSkillDto(
    UUID id,
    UUID projectId,
    String name,
    String description,
    String instructions,
    boolean enabled,
    String createdBy,
    Instant createdAt,
    Instant updatedAt) {

  public static AiSkillDto from(AiSkill s) {
    return new AiSkillDto(
        s.getId(),
        s.getProjectId(),
        s.getName(),
        s.getDescription(),
        s.getInstructions(),
        s.isEnabled(),
        s.getCreatedBy(),
        s.getCreatedAt(),
        s.getUpdatedAt());
  }
}
