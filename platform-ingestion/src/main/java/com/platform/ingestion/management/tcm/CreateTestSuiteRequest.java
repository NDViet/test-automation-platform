package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;

public record CreateTestSuiteRequest(
        @NotBlank String name,
        String description
) {}
