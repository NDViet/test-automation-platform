package com.platform.ingestion.parser;

public record ParseContext(
        String runId,
        String teamId,
        String projectId,
        String branch,
        String environment,
        String commitSha,
        String ciProvider,
        String ciRunUrl,
        // Execution metadata
        String executionMode,   // PARALLEL | SEQUENTIAL | UNKNOWN
        int    parallelism,     // thread/fork count; 0 = unknown
        String suiteName        // top-level suite / class / feature file
) {}
