package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestSuite;

public record TestSuiteDto(
        String id,
        String projectId,
        String name,
        String description,
        String parentId,
        String planType,
        boolean active,
        // ownership scope
        String areaPath,
        String teamId,
        String teamName,
        // membership
        String selectionMode,       // STATIC | SMART
        String filterIteration,
        String filterStatus,
        String filterTags,
        int caseCount,              // resolved case count (members or smart match)
        String createdAt,
        String updatedAt
) {
    public static TestSuiteDto from(TestSuite s) {
        return from(s, null, 0);
    }

    public static TestSuiteDto from(TestSuite s, String teamName, int caseCount) {
        return new TestSuiteDto(
                s.getId() != null ? s.getId().toString() : null,
                s.getProjectId() != null ? s.getProjectId().toString() : null,
                s.getName(),
                s.getDescription(),
                s.getParentId() != null ? s.getParentId().toString() : null,
                s.getPlanType(),
                s.isActive(),
                s.getAreaPath(),
                s.getTeamId() != null ? s.getTeamId().toString() : null,
                teamName,
                s.getSelectionMode(),
                s.getFilterIteration(),
                s.getFilterStatus(),
                s.getFilterTags(),
                caseCount,
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null
        );
    }
}
