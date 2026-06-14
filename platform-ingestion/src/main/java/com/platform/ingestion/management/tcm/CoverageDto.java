package com.platform.ingestion.management.tcm;

import java.util.List;

/** Requirements coverage report for a project. */
public record CoverageDto(
        int totalRequirements,
        int coveredByAutomation,
        int coveredManualOnly,
        int uncovered,
        double automationCoveragePct,
        List<Row> requirements
) {
    /** One requirement's coverage by curated test cases. */
    public record Row(
            String requirementId,
            String externalId,
            String title,
            String issueType,
            String requirementStatus,
            int automatedCases,
            int manualCases,
            String lastStatus       // last observed result across linked cases, or null
    ) {}
}
