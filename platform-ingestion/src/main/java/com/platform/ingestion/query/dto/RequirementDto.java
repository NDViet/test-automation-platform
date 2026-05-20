package com.platform.ingestion.query.dto;

import com.platform.core.domain.PlatformRequirement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequirementDto(
        UUID id,
        UUID projectId,
        String externalId,
        String title,
        String description,
        String issueType,
        String status,
        String priority,
        int depth,
        UUID parentId,
        List<Object> acceptanceCriteria,
        String changeSummary,
        Instant syncedAt,
        Instant updatedAt
) {
    public static RequirementDto from(PlatformRequirement r) {
        return new RequirementDto(
                r.getId(),
                r.getProjectId(),
                r.getExternalId(),
                r.getTitle(),
                r.getDescription(),
                r.getIssueType(),
                r.getStatus(),
                r.getPriority(),
                r.getDepth(),
                r.getParentId(),
                r.getAcceptanceCriteria(),
                r.getChangeSummary(),
                r.getSyncedAt(),
                r.getUpdatedAt()
        );
    }
}
