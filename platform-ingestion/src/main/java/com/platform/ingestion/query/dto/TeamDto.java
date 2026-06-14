package com.platform.ingestion.query.dto;
import java.time.Instant;
import java.util.UUID;
public record TeamDto(UUID id, UUID projectId, String name, String slug, Instant createdAt) {
    public static TeamDto from(com.platform.core.domain.Team t) {
        return new TeamDto(t.getId(), t.getProjectId(), t.getName(), t.getSlug(), t.getCreatedAt());
    }
}
