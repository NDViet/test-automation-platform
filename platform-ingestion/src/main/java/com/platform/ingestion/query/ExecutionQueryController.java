package com.platform.ingestion.query;

import com.platform.ingestion.query.dto.ExecutionDetailDto;
import com.platform.ingestion.query.dto.ExecutionSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Executions", description = "Test execution query endpoints")
public class ExecutionQueryController {

    private final ExecutionQueryService executionQueryService;

    public ExecutionQueryController(ExecutionQueryService executionQueryService) {
        this.executionQueryService = executionQueryService;
    }

    @GetMapping("/api/v1/projects/{projectId}/executions")
    @Operation(summary = "List recent executions for a project")
    public List<ExecutionSummaryDto> listByProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "20") int limit) {
        return executionQueryService.findByProject(projectId, Math.min(limit, 100));
    }

    @GetMapping("/api/v1/executions/{runId}")
    @Operation(summary = "Get full execution detail including all test case results")
    public ResponseEntity<ExecutionDetailDto> getByRunId(@PathVariable String runId) {
        return executionQueryService.findByRunId(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
