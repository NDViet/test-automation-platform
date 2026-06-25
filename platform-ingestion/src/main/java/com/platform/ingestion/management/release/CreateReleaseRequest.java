package com.platform.ingestion.management.release;

import jakarta.validation.constraints.NotBlank;

/** Create/update payload for a platform release (+ optional COMPOSITE upstream mapping). */
public record CreateReleaseRequest(
    @NotBlank String name,
    String releaseType, // VERSION (default) | SPRINT | MILESTONE
    String externalId,
    String targetDate, // ISO date (yyyy-MM-dd) or null
    String state, // PLANNED (default) | IN_PROGRESS | RELEASED | ARCHIVED
    // ── Composite mapping (any subset; AND-combined). All blank = standalone. ──
    String mapIterationPath,
    String mapAreaPath,
    String mapTeamId,
    String mapTag,
    String mappingField, // advanced: upstream field ref name
    String mappingValue // advanced: expected field value
    ) {}
