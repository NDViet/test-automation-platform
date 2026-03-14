package com.platform.ingestion.query.dto;
import java.time.Instant;
import java.util.UUID;
public record ProjectDto(UUID id, String name, String slug, UUID teamId, String teamName, String teamSlug, Instant createdAt) {
    public static ProjectDto from(com.platform.core.domain.Project p) {
        return new ProjectDto(p.getId(), p.getName(), p.getSlug(),
                p.getTeam().getId(), p.getTeam().getName(), p.getTeam().getSlug(),
                p.getCreatedAt());
    }
}
