package com.platform.ingestion.query.dto;
import java.time.Instant;
import java.util.UUID;
public record TeamDto(UUID id, String name, String slug, Instant createdAt) {
    public static TeamDto from(com.platform.core.domain.Team t) {
        return new TeamDto(t.getId(), t.getName(), t.getSlug(), t.getCreatedAt());
    }
}
