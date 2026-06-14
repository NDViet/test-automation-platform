package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateTestRunRequest(
        @NotBlank String name,
        String releaseVersion,
        String environment,
        String triggeredBy,
        List<String> testCaseIds,
        String environmentId,   // optional — named Environment (V50); overrides `environment` label
        String matrixType       // optional — FULL (default) or PAIRWISE for parametrized cases
) {}
