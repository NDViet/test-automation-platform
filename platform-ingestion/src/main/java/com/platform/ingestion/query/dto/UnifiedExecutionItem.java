package com.platform.ingestion.query.dto;

import java.time.Instant;

/**
 * A single row in the unified Test Execution list — covers both manual TestRuns and
 * automated CI TestExecutions. Discriminated by {@code type}.
 */
public record UnifiedExecutionItem(
        String id,
        /** "MANUAL" or "AUTOMATED" */
        String type,
        /** Display name: TestRun.name for manual, workflow/branch·sha for automated. */
        String name,
        String status,
        String environment,
        // ── Automated-only ────────────────────────────────────────────────────
        String ciProvider,
        String branch,
        String commitSha,
        String workflow,
        String triggerType,
        // ── Counts ────────────────────────────────────────────────────────────
        long totalTests,
        long passed,
        long failed,
        long blocked,   // manual: BLOCKED; automated: always 0
        long skipped,
        long pending,   // manual: PENDING; automated: always 0
        long broken,    // automated only (timedOut/interrupted)
        double passRate,
        long durationMs,
        // ── Scope dimensions ──────────────────────────────────────────────────
        String teamId,
        String teamName,
        String areaPath,       // manual: ADO areaPath; automated: areaSlug
        String iterationPath,  // manual only
        // ── Manual-only ───────────────────────────────────────────────────────
        String releaseId,
        String releaseName,
        String releaseVersion,
        String triggeredBy,
        // ── Automated-only ────────────────────────────────────────────────────
        String ciRunUrl,
        /**
         * Navigation key: for MANUAL = TestRun UUID string; for AUTOMATED = CI run ID.
         * Frontend uses this to build the detail URL.
         */
        String runId,
        Instant date
) {}
