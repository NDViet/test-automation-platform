package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;

public record CreateTestSuiteRequest(
        @NotBlank String name,
        String description,
        String parentId,        // optional — parent suite id for the plan tree
        String planType,        // optional — SMOKE, REGRESSION, SANITY, FEATURE, DOMAIN, …
        Boolean active,         // optional — defaults to true
        // ── Ownership scope ──
        String areaPath,
        String teamId,
        // ── Membership ──
        String selectionMode,   // STATIC (default) | SMART
        // ── SMART filter (when selectionMode = SMART) ──
        String filterIteration,
        String filterStatus,
        String filterTags       // comma-separated; match ANY
) {}
