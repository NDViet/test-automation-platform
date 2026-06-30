package com.platform.ingestion.dashboard;

import com.platform.ingestion.dashboard.TestExecutionMonitorService.Dimension;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Read API for the Test Execution monitor (rollups by Release / Sprint / Area / Team). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/test-execution")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
public class TestExecutionMonitorController {

  private final TestExecutionMonitorService service;

  public TestExecutionMonitorController(TestExecutionMonitorService service) {
    this.service = service;
  }

  /**
   * Primary view: releases grouped by Team with pass-rate + coverage; scoped by
   * area/team/iteration.
   */
  @GetMapping("/release-board")
  public TestExecutionMonitorService.ReleaseBoard releaseBoard(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String iteration,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team) {
    return service.releaseBoard(projectId, iteration, area, team);
  }

  @GetMapping("/by-release")
  public TestExecutionMonitorService.MonitorOverview byRelease(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team,
      @RequestParam(required = false) String iteration) {
    return service.byDimension(projectId, Dimension.RELEASE, area, team, iteration);
  }

  @GetMapping("/by-sprint")
  public TestExecutionMonitorService.MonitorOverview bySprint(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team,
      @RequestParam(required = false) String iteration) {
    return service.byDimension(projectId, Dimension.SPRINT, area, team, iteration);
  }

  @GetMapping("/by-area")
  public TestExecutionMonitorService.MonitorOverview byArea(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team,
      @RequestParam(required = false) String iteration) {
    return service.byDimension(projectId, Dimension.AREA, area, team, iteration);
  }

  @GetMapping("/by-team")
  public TestExecutionMonitorService.MonitorOverview byTeam(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team,
      @RequestParam(required = false) String iteration) {
    return service.byDimension(projectId, Dimension.TEAM, area, team, iteration);
  }

  /**
   * Drill-down: runs for one group of a dimension. {@code value} omitted = the unassigned group.
   */
  @GetMapping("/runs")
  public List<TestExecutionMonitorService.RunSummary> runs(
      @PathVariable UUID projectId,
      @RequestParam String dimension,
      @RequestParam(required = false) String value) {
    return service.runs(projectId, TestExecutionMonitorService.parseDimension(dimension), value);
  }
}
