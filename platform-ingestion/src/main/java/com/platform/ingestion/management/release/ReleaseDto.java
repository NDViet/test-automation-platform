package com.platform.ingestion.management.release;

import com.platform.core.domain.SotRelease;

/** A platform-owned release, its composite upstream mapping, and derived counts. */
public record ReleaseDto(
    String id,
    String projectId,
    String name,
    String releaseType,
    String externalId,
    String targetDate,
    String state,
    // composite mapping
    String mapIterationPath,
    String mapAreaPath,
    String mapTeamId,
    String mapTeamName,
    String mapTag,
    String mappingField,
    String mappingValue,
    long mappedRequirementCount,
    long linkedRunCount,
    String createdAt) {
  public static ReleaseDto from(
      SotRelease r, String teamName, long mappedRequirementCount, long linkedRunCount) {
    return new ReleaseDto(
        r.getId() != null ? r.getId().toString() : null,
        r.getProjectId() != null ? r.getProjectId().toString() : null,
        r.getName(),
        r.getReleaseType(),
        r.getExternalId(),
        r.getTargetDate() != null ? r.getTargetDate().toString() : null,
        r.getState(),
        r.getMapIterationPath(),
        r.getMapAreaPath(),
        r.getMapTeamId() != null ? r.getMapTeamId().toString() : null,
        teamName,
        r.getMapTag(),
        r.getMappingField(),
        r.getMappingValue(),
        mappedRequirementCount,
        linkedRunCount,
        r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
  }
}
