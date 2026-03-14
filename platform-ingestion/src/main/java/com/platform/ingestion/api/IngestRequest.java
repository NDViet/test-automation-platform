package com.platform.ingestion.api;

import com.platform.common.enums.SourceFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IngestRequest(
        @NotBlank String teamId,
        @NotBlank String projectId,
        @NotNull  SourceFormat format,
        String branch,
        String environment,
        String commitSha,
        String ciRunUrl,
        // Execution metadata — optional, enriched by test framework or CI pipeline
        String executionMode,   // PARALLEL | SEQUENTIAL | UNKNOWN
        Integer parallelism,    // thread/fork count; null means unknown (stored as 0)
        String suiteName        // top-level suite / class / feature file
) {}
