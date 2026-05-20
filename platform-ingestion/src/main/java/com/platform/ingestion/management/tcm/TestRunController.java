package com.platform.ingestion.management.tcm;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/test-runs")
@Tag(name = "Test Case Management")
public class TestRunController {

    private final TestRunService service;

    public TestRunController(TestRunService service) {
        this.service = service;
    }

    @GetMapping
    public List<TestRunDto> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @PostMapping
    public ResponseEntity<TestRunDto> create(@PathVariable UUID projectId,
                                              @Valid @RequestBody CreateTestRunRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req));
    }

    @GetMapping("/{runId}")
    public TestRunDto get(@PathVariable UUID projectId,
                           @PathVariable UUID runId) {
        return service.get(projectId, runId);
    }

    @DeleteMapping("/{runId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                        @PathVariable UUID runId) {
        service.delete(projectId, runId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runId}/complete")
    public TestRunDto complete(@PathVariable UUID projectId,
                                @PathVariable UUID runId) {
        return service.complete(projectId, runId);
    }

    @GetMapping("/{runId}/executions")
    public List<TestCaseExecutionDto> listExecutions(@PathVariable UUID projectId,
                                                      @PathVariable UUID runId) {
        return service.listExecutions(projectId, runId);
    }

    @PutMapping("/{runId}/executions/{execId}")
    public TestCaseExecutionDto updateExecution(@PathVariable UUID projectId,
                                                 @PathVariable UUID runId,
                                                 @PathVariable UUID execId,
                                                 @Valid @RequestBody UpdateExecutionRequest req) {
        return service.updateExecution(projectId, runId, execId, req);
    }
}
