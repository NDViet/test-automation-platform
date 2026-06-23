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
        String areaPath,
        String iterationPath,
        List<Object> acceptanceCriteria,
        String changeSummary,
        Instant createdDate,
        Instant syncedAt,
        Instant updatedAt,
        String sourceUrl
) {
    public static RequirementDto from(PlatformRequirement r) {
        return from(r, null);
    }

    /**
     * @param sourceUrlBase optional "open original" base (e.g. ADO work-item edit URL,
     *                      ending in '/'); appended with the external id when both are present.
     */
    public static RequirementDto from(PlatformRequirement r, String sourceUrlBase) {
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
                r.getAreaPath(),
                r.getIterationPath(),
                r.getAcceptanceCriteria(),
                r.getChangeSummary(),
                r.getCreatedDate(),
                r.getSyncedAt(),
                r.getUpdatedAt(),
                sourceUrl(sourceUrlBase, r.getExternalId())
        );
    }

    /** Build the source URL only for numeric external ids (ADO work items) with a known base. */
    private static String sourceUrl(String base, String externalId) {
        if (base == null || externalId == null || !externalId.matches("\\d+")) return null;
        return base + externalId;
    }
}
