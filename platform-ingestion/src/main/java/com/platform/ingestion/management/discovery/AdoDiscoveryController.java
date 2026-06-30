package com.platform.ingestion.management.discovery;

import com.platform.ingestion.management.discovery.dto.SchemaDriftReport;
import com.platform.security.web.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/**
 * Azure DevOps schema discovery + mapping suggestions for a project. Uses the project's resolved
 * AZURE_DEVOPS_BOARDS credential (Org→Team→Project cascade).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/ado")
@Tag(name = "Integration Discovery")
public class AdoDiscoveryController {

  private final AdoDiscoveryService service;
  private final SchemaDriftService driftService;

  public AdoDiscoveryController(AdoDiscoveryService service, SchemaDriftService driftService) {
    this.service = service;
    this.driftService = driftService;
  }

  @GetMapping("/projects")
  public List<AdoDiscoveryService.AdoProject> projects(@PathVariable UUID projectId) {
    return service.listProjects(projectId);
  }

  @GetMapping("/work-item-types")
  public List<AdoDiscoveryService.TypeSummary> types(
      @PathVariable UUID projectId, @RequestParam String adoProject) {
    return service.listTypes(projectId, adoProject);
  }

  @GetMapping("/work-item-types/{type}/schema")
  public AdoDiscoveryService.TypeSchema schema(
      @PathVariable UUID projectId, @PathVariable String type, @RequestParam String adoProject) {
    return service.typeSchema(projectId, adoProject, type);
  }

  /** Drift of the live schema vs the captured baseline (captures a baseline if none exists). */
  @GetMapping("/work-item-types/{type}/drift")
  public SchemaDriftReport drift(
      @PathVariable UUID projectId,
      @PathVariable String type,
      @RequestParam String adoProject) {
    String actor = CurrentUser.username();
    return driftService.report(projectId, adoProject, type, actor);
  }

  /** (Re)capture the baseline = accept the current upstream schema. */
  @PostMapping("/work-item-types/{type}/drift/baseline")
  public SchemaDriftReport captureBaseline(
      @PathVariable UUID projectId,
      @PathVariable String type,
      @RequestParam String adoProject) {
    String actor = CurrentUser.username();
    return driftService.captureBaseline(projectId, adoProject, type, actor);
  }
}
