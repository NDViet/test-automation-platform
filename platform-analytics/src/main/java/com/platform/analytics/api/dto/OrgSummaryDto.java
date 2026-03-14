package com.platform.analytics.api.dto;

import java.util.List;

public record OrgSummaryDto(
        int totalProjects,
        int totalRuns,
        double overallPassRate,
        int criticalFlakyTests,
        List<ProjectSummary> projects
) {
    public record ProjectSummary(
            String projectId,
            String teamId,
            double passRate,
            int totalRuns,
            int flakyTests
    ) {}
}
