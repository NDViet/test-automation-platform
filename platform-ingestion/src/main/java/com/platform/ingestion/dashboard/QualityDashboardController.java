package com.platform.ingestion.dashboard;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Read API for the project Quality dashboards. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/quality")
public class QualityDashboardController {

  private final QualityDashboardService service;

  public QualityDashboardController(QualityDashboardService service) {
    this.service = service;
  }

  @GetMapping("/overview")
  public QualityDashboardService.Overview overview(@PathVariable UUID projectId) {
    return service.overview(projectId);
  }

  @GetMapping("/engineers")
  public List<QualityDashboardService.EngineerStat> engineers(@PathVariable UUID projectId) {
    return service.engineers(projectId);
  }

  /** Drill-down: the work items behind an engineer's KPI cell. */
  @GetMapping("/work-items")
  public List<QualityDashboardService.WorkItemRef> workItems(
      @PathVariable UUID projectId,
      @RequestParam String person,
      @RequestParam(defaultValue = "assignee") String attribution,
      @RequestParam(defaultValue = "any") String type,
      @RequestParam(defaultValue = "any") String status) {
    return service.workItems(projectId, person, attribution, type, status);
  }

  /** Activity timeline: a QE's recent history events. */
  @GetMapping("/activity")
  public List<QualityDashboardService.ActivityEvent> activity(
      @PathVariable UUID projectId,
      @RequestParam String person,
      @RequestParam(defaultValue = "50") int limit) {
    return service.activity(projectId, person, limit);
  }

  /** Drill-down for history involvement metrics: kind = resolved | participated | reopened. */
  @GetMapping("/involvement-items")
  public List<QualityDashboardService.WorkItemRef> involvementItems(
      @PathVariable UUID projectId, @RequestParam String person, @RequestParam String kind) {
    return service.involvementItems(projectId, person, kind);
  }
}
