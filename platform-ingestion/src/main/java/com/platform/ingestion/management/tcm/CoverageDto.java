package com.platform.ingestion.management.tcm;

import java.util.List;

/** Requirements coverage report for a project, with Area/Team rollups. */
public record CoverageDto(
    int totalRequirements,
    int coveredByAutomation,
    int coveredManualOnly,
    int uncovered,
    double automationCoveragePct,
    List<GroupStat> byArea,
    List<GroupStat> byTeam,
    List<Row> requirements) {
  /** Coverage rollup for one Area or Team. */
  public record GroupStat(
      String label,
      int total,
      int covered, // automated or manual
      int coveredByAutomation,
      int manualOnly,
      int uncovered,
      double coveragePct, // covered / total
      double automationPct // automated / total
      ) {}

  /** One requirement's coverage by curated test cases. */
  public record Row(
      String requirementId,
      String externalId,
      String title,
      String issueType,
      String requirementStatus,
      String areaPath,
      String teamName,
      int automatedCases,
      int manualCases,
      String lastStatus // last observed result across linked cases, or null
      ) {}
}
