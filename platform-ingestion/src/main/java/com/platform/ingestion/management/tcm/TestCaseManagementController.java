package com.platform.ingestion.management.tcm;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/test-cases")
@Tag(name = "Test Case Management")
public class TestCaseManagementController {

    private final TestCaseManagementService service;

    public TestCaseManagementController(TestCaseManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManagedTestCaseDto> list(@PathVariable UUID projectId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String suiteId,
                                         @RequestParam(required = false) String search) {
        return service.list(projectId, status, suiteId, search);
    }

    @PostMapping
    public ResponseEntity<ManagedTestCaseDto> create(@PathVariable UUID projectId,
                                                      @Valid @RequestBody CreateTestCaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req));
    }

    @GetMapping("/{tcId}")
    public ManagedTestCaseDto get(@PathVariable UUID projectId,
                                   @PathVariable UUID tcId) {
        return service.get(projectId, tcId);
    }

    @PutMapping("/{tcId}")
    public ManagedTestCaseDto update(@PathVariable UUID projectId,
                                      @PathVariable UUID tcId,
                                      @RequestBody UpdateTestCaseRequest req) {
        return service.update(projectId, tcId, req);
    }

    @DeleteMapping("/{tcId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                        @PathVariable UUID tcId) {
        service.delete(projectId, tcId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{tcId}/steps")
    public ManagedTestCaseDto replaceSteps(@PathVariable UUID projectId,
                                            @PathVariable UUID tcId,
                                            @RequestBody List<CreateTestCaseRequest.StepRequest> steps) {
        return service.replaceSteps(projectId, tcId, steps);
    }

    @PostMapping("/{tcId}/submit-review")
    public ManagedTestCaseDto submitForReview(@PathVariable UUID projectId,
                                               @PathVariable UUID tcId) {
        return service.submitForReview(projectId, tcId);
    }

    @PostMapping("/{tcId}/approve")
    public ManagedTestCaseDto approve(@PathVariable UUID projectId,
                                       @PathVariable UUID tcId) {
        return service.approve(projectId, tcId);
    }

    @PostMapping("/{tcId}/reject")
    public ManagedTestCaseDto reject(@PathVariable UUID projectId,
                                      @PathVariable UUID tcId) {
        return service.reject(projectId, tcId);
    }

    @PostMapping("/{tcId}/generate-automation")
    public ManagedTestCaseDto triggerAutomationGeneration(
            @PathVariable UUID projectId,
            @PathVariable UUID tcId,
            @RequestParam(required = false) String githubConfigId) {
        return service.triggerAutomationGeneration(projectId, tcId, githubConfigId);
    }
}
