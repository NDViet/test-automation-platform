package com.platform.ingestion.management.tcm;

import com.platform.core.domain.Environment;

import java.util.Map;

public record EnvironmentDto(
        String id,
        String projectId,
        String name,
        String description,
        Map<String, String> properties,
        String createdAt
) {
    public static EnvironmentDto from(Environment e, Map<String, String> properties) {
        return new EnvironmentDto(
                e.getId() != null ? e.getId().toString() : null,
                e.getProjectId() != null ? e.getProjectId().toString() : null,
                e.getName(),
                e.getDescription(),
                properties,
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null
        );
    }
}
