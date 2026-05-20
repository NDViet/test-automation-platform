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
        String startedAt,
        String completedAt,
        String createdAt
) {
    public static TestRunDto from(TestRun run, long total, long passed, long failed,
                                  long blocked, long skipped, long pending) {
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
                run.getStartedAt() != null ? run.getStartedAt().toString() : null,
                run.getCompletedAt() != null ? run.getCompletedAt().toString() : null,
                run.getCreatedAt() != null ? run.getCreatedAt().toString() : null
        );
    }
}
