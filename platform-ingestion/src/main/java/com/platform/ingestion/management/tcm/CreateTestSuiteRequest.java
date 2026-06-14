package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;

public record CreateTestSuiteRequest(
        @NotBlank String name,
        String description,
        String parentId,    // optional — parent suite id for the plan tree
        String planType,    // optional — SMOKE, REGRESSION, SANITY, …
        Boolean active      // optional — defaults to true
) {}
