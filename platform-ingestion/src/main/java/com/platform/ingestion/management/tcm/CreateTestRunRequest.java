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
        String matrixType,      // optional — FULL (default) or PAIRWISE for parametrized cases
        List<String> suiteIds,  // optional — reusable suites; their resolved cases are unioned in
        // ── Monitoring dimensions (run-level tags) ──
        String releaseId,       // optional — platform release (sot_releases) this run validates
        String iterationPath,   // optional — ADO iteration (Sprint) path
        String areaPath,        // optional — ADO area path
        String teamId           // optional — owning ADO team (ado_teams)
) {}
