package com.platform.ingestion.query.dto;
import java.time.Instant;
import java.util.UUID;
public record ExecutionSummaryDto(
        UUID id, String runId, UUID projectId, String projectSlug, String projectName,
        String branch, String environment, String commitSha,
        String executionMode, int parallelism, String suiteName,
        String sourceFormat, String ciProvider, String ciRunUrl,
        int totalTests, int passed, int failed, int skipped, int broken,
        Long durationMs, double passRate, Instant executedAt, Instant ingestedAt) {

    public static ExecutionSummaryDto from(com.platform.core.domain.TestExecution e) {
        double passRate = e.getTotalTests() > 0
                ? (double) e.getPassed() / e.getTotalTests() * 100.0
                : 0.0;
        return new ExecutionSummaryDto(
                e.getId(), e.getRunId(), e.getProject().getId(),
                e.getProject().getSlug(), e.getProject().getName(),
                e.getBranch(), e.getEnvironment(), e.getCommitSha(),
                e.getExecutionMode(), e.getParallelism(), e.getSuiteName(),
                e.getSourceFormat() != null ? e.getSourceFormat().name() : null,
                e.getCiProvider(), e.getCiRunUrl(),
                e.getTotalTests(), e.getPassed(), e.getFailed(),
                e.getSkipped(), e.getBroken(),
                e.getDurationMs(), passRate, e.getExecutedAt(), e.getIngestedAt());
    }
}
