package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestRun;

public record TestRunDto(
        String id,
        String projectId,
        String name,
        String releaseVersion,
        String environment,
        String status,
        String triggeredBy,
        long totalTests,
        long passed,
        long failed,
        long blocked,
        long skipped,
        long pending,
        // ── Monitoring dimensions (run-level tags) ──
        String releaseId,
        String releaseName,
        String iterationPath,
        String areaPath,
        String teamId,
        String teamName,
        String startedAt,
        String completedAt,
        String createdAt
) {
    public static TestRunDto from(TestRun run, long total, long passed, long failed,
                                  long blocked, long skipped, long pending) {
        return from(run, total, passed, failed, blocked, skipped, pending, null, null);
    }

    /** Variant that carries resolved release/team display names (looked up by the caller). */
    public static TestRunDto from(TestRun run, long total, long passed, long failed,
                                  long blocked, long skipped, long pending,
                                  String releaseName, String teamName) {
        return new TestRunDto(
                run.getId() != null ? run.getId().toString() : null,
                run.getProjectId() != null ? run.getProjectId().toString() : null,
                run.getName(),
                run.getReleaseVersion(),
                run.getEnvironment(),
                run.getStatus(),
                run.getTriggeredBy(),
                total,
                passed,
                failed,
                blocked,
                skipped,
                pending,
                run.getReleaseId() != null ? run.getReleaseId().toString() : null,
                releaseName,
                run.getIterationPath(),
                run.getAreaPath(),
                run.getTeamId() != null ? run.getTeamId().toString() : null,
                teamName,
                run.getStartedAt() != null ? run.getStartedAt().toString() : null,
                run.getCompletedAt() != null ? run.getCompletedAt().toString() : null,
                run.getCreatedAt() != null ? run.getCreatedAt().toString() : null
        );
    }
}
