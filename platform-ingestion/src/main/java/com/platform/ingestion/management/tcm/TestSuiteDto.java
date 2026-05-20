package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestSuite;

public record TestSuiteDto(
        String id,
        String projectId,
        String name,
        String description,
        String createdAt,
        String updatedAt
) {
    public static TestSuiteDto from(TestSuite s) {
        return new TestSuiteDto(
                s.getId() != null ? s.getId().toString() : null,
                s.getProjectId() != null ? s.getProjectId().toString() : null,
                s.getName(),
                s.getDescription(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null
        );
    }
}
