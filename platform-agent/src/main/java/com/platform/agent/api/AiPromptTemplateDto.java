package com.platform.agent.api;

import com.platform.core.domain.AiPromptTemplate;
import java.time.Instant;
import java.util.UUID;

/** API view of an {@link AiPromptTemplate}. */
public record AiPromptTemplateDto(
    UUID id,
    UUID projectId,
    String kind,
    String name,
    String body,
    boolean isDefault,
    String createdBy,
    Instant createdAt,
    Instant updatedAt) {

  public static AiPromptTemplateDto from(AiPromptTemplate t) {
    return new AiPromptTemplateDto(
        t.getId(),
        t.getProjectId(),
        t.getKind(),
        t.getName(),
        t.getBody(),
        t.isDefault(),
        t.getCreatedBy(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }
}
