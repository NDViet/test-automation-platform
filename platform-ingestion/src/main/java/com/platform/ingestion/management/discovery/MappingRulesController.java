package com.platform.ingestion.management.discovery;

import com.platform.ingestion.management.discovery.dto.MappingRulesetView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Manage mapping-rule overrides for the Mapping Suggester. Resolution: PROJECT → ORG → built-in
 * default. DELETE resets to the parent scope.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Mapping Rules")
public class MappingRulesController {

  private final MappingRulesService service;

  public MappingRulesController(MappingRulesService service) {
    this.service = service;
  }

  public record SaveRulesRequest(String json) {}

  /** The built-in default ruleset (baseline for "Reset to default"). */
  @GetMapping("/mapping-rules/default")
  public MappingRulesetView getDefault() {
    return service.getDefault();
  }

  // ── ORG ──────────────────────────────────────────────────────────────────────

  @GetMapping("/organizations/{orgId}/mapping-rules")
  public MappingRulesetView getOrg(@PathVariable UUID orgId) {
    return service.getOrg(orgId);
  }

  @PutMapping("/organizations/{orgId}/mapping-rules")
  public MappingRulesetView saveOrg(
      @PathVariable UUID orgId,
      @RequestBody SaveRulesRequest req,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return service.saveOrg(orgId, req.json(), actor);
  }

  @DeleteMapping("/organizations/{orgId}/mapping-rules")
  public ResponseEntity<Void> resetOrg(@PathVariable UUID orgId) {
    service.resetOrg(orgId);
    return ResponseEntity.noContent().build();
  }

  // ── PROJECT ───────────────────────────────────────────────────────────────────

  @GetMapping("/projects/{projectId}/mapping-rules")
  public MappingRulesetView getProject(@PathVariable UUID projectId) {
    return service.getProject(projectId);
  }

  @PutMapping("/projects/{projectId}/mapping-rules")
  public MappingRulesetView saveProject(
      @PathVariable UUID projectId,
      @RequestBody SaveRulesRequest req,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return service.saveProject(projectId, req.json(), actor);
  }

  @DeleteMapping("/projects/{projectId}/mapping-rules")
  public ResponseEntity<Void> resetProject(@PathVariable UUID projectId) {
    service.resetProject(projectId);
    return ResponseEntity.noContent().build();
  }
}
