package com.platform.ingestion.management.tcm;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/test-suites")
@Tag(name = "Test Case Management")
public class TestSuiteController {

  private final TestSuiteService service;

  public TestSuiteController(TestSuiteService service) {
    this.service = service;
  }

  @GetMapping
  public List<TestSuiteDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @PostMapping
  public ResponseEntity<TestSuiteDto> create(
      @PathVariable UUID projectId, @Valid @RequestBody CreateTestSuiteRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req));
  }

  @PutMapping("/{suiteId}")
  public TestSuiteDto update(
      @PathVariable UUID projectId,
      @PathVariable UUID suiteId,
      @Valid @RequestBody CreateTestSuiteRequest req) {
    return service.update(projectId, suiteId, req);
  }

  @DeleteMapping("/{suiteId}")
  public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID suiteId) {
    service.delete(projectId, suiteId);
    return ResponseEntity.noContent().build();
  }

  /** Resolved cases for a suite (static members or smart filter). */
  @GetMapping("/{suiteId}/cases")
  public List<SelectableTestCaseDto> cases(
      @PathVariable UUID projectId, @PathVariable UUID suiteId) {
    return service.cases(projectId, suiteId);
  }

  public record MembersRequest(List<String> testCaseIds) {}

  /** Replace the static membership of a suite. */
  @PutMapping("/{suiteId}/members")
  public ResponseEntity<Void> replaceMembers(
      @PathVariable UUID projectId, @PathVariable UUID suiteId, @RequestBody MembersRequest req) {
    service.replaceMembers(projectId, suiteId, req.testCaseIds());
    return ResponseEntity.noContent().build();
  }
}
