package com.platform.ingestion.management.tcm;

/**
 * Edit an in-progress run's scope (release / sprint / area / team) and environment. All fields are
 * optional; a provided dimension overwrites the current value, a blank one clears it.
 */
public record UpdateRunRequest(
    String name,
    String environment,
    String environmentId,
    String releaseId,
    String iterationPath,
    String areaPath,
    String teamId) {}
