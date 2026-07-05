package com.platform.ingestion.management.schedule;

import java.util.Map;

/** A CI run schedule as returned to the portal. */
public record RunScheduleDto(
    String id,
    String name,
    String repoFullName,
    long workflowId,
    String ref,
    Map<String, String> inputs,
    int intervalMinutes,
    String nextRunAt,
    boolean enabled,
    String lastDispatchedAt,
    String createdAt) {}
