package com.platform.ingestion.management.tcm;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/test-cases")
@Tag(name = "Test Case Management")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
public class TestCaseManagementController {

  private final TestCaseManagementService service;

  public TestCaseManagementController(TestCaseManagementService service) {
    this.service = service;
  }

  @GetMapping
  public List<ManagedTestCaseDto> list(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String suiteId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String teamId,
      @RequestParam(required = false) String iteration) {
    return service.list(projectId, status, suiteId, search, area, teamId, iteration);
  }

  /** Scope-filtered, searchable picker for run creation (by area/iteration/team + search). */
  @GetMapping("/selectable")
  public List<SelectableTestCaseDto> selectable(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String iteration,
      @RequestParam(required = false) String teamId,
      @RequestParam(required = false) String q) {
    return service.selectable(projectId, status, area, iteration, teamId, q);
  }

  @PostMapping
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<ManagedTestCaseDto> create(
      @PathVariable UUID projectId, @Valid @RequestBody CreateTestCaseRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req));
  }

  @GetMapping("/{tcId}")
  public ManagedTestCaseDto get(@PathVariable UUID projectId, @PathVariable UUID tcId) {
    return service.get(projectId, tcId);
  }

  @PutMapping("/{tcId}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto update(
      @PathVariable UUID projectId,
      @PathVariable UUID tcId,
      @RequestBody UpdateTestCaseRequest req) {
    return service.update(projectId, tcId, req);
  }

  @DeleteMapping("/{tcId}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID tcId) {
    service.delete(projectId, tcId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{tcId}/steps")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto replaceSteps(
      @PathVariable UUID projectId,
      @PathVariable UUID tcId,
      @RequestBody List<CreateTestCaseRequest.StepRequest> steps) {
    return service.replaceSteps(projectId, tcId, steps);
  }

  @PostMapping("/{tcId}/submit-review")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto submitForReview(@PathVariable UUID projectId, @PathVariable UUID tcId) {
    return service.submitForReview(projectId, tcId);
  }

  @PostMapping("/{tcId}/approve")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto approve(@PathVariable UUID projectId, @PathVariable UUID tcId) {
    return service.approve(projectId, tcId);
  }

  @PostMapping("/{tcId}/reject")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto reject(@PathVariable UUID projectId, @PathVariable UUID tcId) {
    return service.reject(projectId, tcId);
  }

  @PostMapping("/{tcId}/generate-automation")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto triggerAutomationGeneration(
      @PathVariable UUID projectId,
      @PathVariable UUID tcId,
      @RequestParam(required = false) String githubConfigId) {
    return service.triggerAutomationGeneration(projectId, tcId, githubConfigId);
  }

  /** STATIC suite ids this case belongs to. */
  @GetMapping("/{tcId}/suites")
  public List<String> caseSuites(@PathVariable UUID projectId, @PathVariable UUID tcId) {
    return service.caseSuites(projectId, tcId);
  }

  public record CaseSuitesRequest(List<String> suiteIds) {}

  /** Replace the case's (static) suite memberships — a case may belong to many suites. */
  @PutMapping("/{tcId}/suites")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Void> setCaseSuites(
      @PathVariable UUID projectId, @PathVariable UUID tcId, @RequestBody CaseSuitesRequest req) {
    service.setCaseSuites(projectId, tcId, req.suiteIds());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{tcId}/link-requirement/{requirementId}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto linkRequirement(
      @PathVariable UUID projectId, @PathVariable UUID tcId, @PathVariable UUID requirementId) {
    return service.linkRequirement(projectId, tcId, requirementId);
  }

  @DeleteMapping("/{tcId}/link-requirement/{requirementId}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto unlinkRequirement(
      @PathVariable UUID projectId, @PathVariable UUID tcId, @PathVariable UUID requirementId) {
    return service.unlinkRequirement(projectId, tcId, requirementId);
  }

  @PostMapping("/{tcId}/apply-suggestion")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ManagedTestCaseDto applyAnalysisSuggestion(
      @PathVariable UUID projectId,
      @PathVariable UUID tcId,
      @Valid @RequestBody ApplySuggestionRequest req) {
    return service.applyAnalysisSuggestion(projectId, tcId, req);
  }
}
