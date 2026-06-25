package com.platform.ingestion.query.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectDto(
    UUID id,
    String name,
    String slug,
    UUID orgId,
    String orgName,
    String orgSlug,
    Instant createdAt) {
  public static ProjectDto from(com.platform.core.domain.Project p) {
    return new ProjectDto(
        p.getId(),
        p.getName(),
        p.getSlug(),
        p.getOrganization().getId(),
        p.getOrganization().getName(),
        p.getOrganization().getSlug(),
        p.getCreatedAt());
  }
}
