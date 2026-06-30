package com.platform.ingestion.dashboard;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Read/config API for the Productivity (cycle-time) dashboard. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/productivity")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
public class ProductivityController {

  private final ProductivityService service;

  public ProductivityController(ProductivityService service) {
    this.service = service;
  }

  @GetMapping("/by-area")
  public ProductivityService.Overview byArea(@PathVariable UUID projectId) {
    return service.byArea(projectId);
  }

  /** Started-but-not-completed items; over=true limits to those past the threshold. */
  @GetMapping("/wip-items")
  public List<ProductivityService.OverThresholdItem> wipItems(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String area,
      @RequestParam(defaultValue = "true") boolean over) {
    return service.wipItems(projectId, area, over);
  }

  @GetMapping("/lead-by-area")
  public ProductivityService.LeadOverview leadByArea(@PathVariable UUID projectId) {
    return service.leadByArea(projectId);
  }

  @GetMapping("/lead-items")
  public List<ProductivityService.LeadItem> leadItems(
      @PathVariable UUID projectId, @RequestParam(required = false) String area) {
    return service.leadItems(projectId, area);
  }

  @GetMapping("/threshold")
  public Map<String, Object> getThreshold(@PathVariable UUID projectId) {
    return Map.of("thresholdHours", service.thresholdHours(projectId));
  }

  public record ThresholdRequest(double thresholdHours) {}

  @PutMapping("/threshold")
  @RequireCapability(value = Capability.MANAGE_PROJECT, scope = "projectId")
  public Map<String, Object> setThreshold(
      @PathVariable UUID projectId, @RequestBody ThresholdRequest req) {
    return Map.of("thresholdHours", service.setThresholdHours(projectId, req.thresholdHours()));
  }
}
