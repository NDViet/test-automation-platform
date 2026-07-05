package com.platform.ingestion.management.schedule;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Create/update payload for a CI run schedule. */
public record RunScheduleForm(
    @NotBlank String name,
    @NotBlank String repoFullName,
    long workflowId,
    String ref,
    Map<String, String> inputs,
    int intervalMinutes,
    boolean enabled) {}
