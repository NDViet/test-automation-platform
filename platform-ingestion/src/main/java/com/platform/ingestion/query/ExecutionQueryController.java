package com.platform.ingestion.query;

import com.platform.ingestion.query.dto.ExecutionDetailDto;
import com.platform.ingestion.query.dto.ExecutionSummaryDto;
import com.platform.ingestion.query.dto.UnifiedExecutionItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
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
        return executionQueryService.findByProject(projectId, Math.min(limit, 500));
    }

    @GetMapping("/api/v1/executions/{runId}")
    @Operation(summary = "Get full execution detail including all test case results")
    public ResponseEntity<ExecutionDetailDto> getByRunId(@PathVariable String runId) {
        return executionQueryService.findByRunId(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Links an automated execution to a sprint / area after the fact, so it appears in
     * sprint reports without re-running or modifying the original CI execution.
     *
     * @param runId          the CI run ID (string, not the DB UUID)
     * @param iterationPath  ADO iteration path, e.g. "MyProject\\Sprint 42" (null = no change)
     * @param areaSlug       area slug (null = no change)
     */
    @PatchMapping("/api/v1/executions/{runId}/scope")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Link an automated execution to a sprint/area for reporting")
    public void updateScope(
            @PathVariable String runId,
            @RequestParam(required = false) String iterationPath,
            @RequestParam(required = false) String areaSlug) {
        executionQueryService.updateScope(runId, iterationPath, areaSlug);
    }

    /**
     * Unified chronological list of manual TestRuns and automated TestExecutions for a project.
     *
     * @param type      ALL (default) | MANUAL | AUTOMATED
     * @param teamId    ADO team UUID — filters manual runs only
     * @param area      area path/slug — filters both manual (areaPath) and automated (areaSlug)
     * @param iteration ADO iteration path — filters manual runs only
     * @param limit     max items to return (default 100, max 500)
     */
    @GetMapping("/api/v1/projects/{projectId}/test-execution/unified")
    @Operation(summary = "Unified chronological list of manual runs and automated executions")
    public List<UnifiedExecutionItem> unifiedList(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String iteration,
            @RequestParam(defaultValue = "100") int limit) {
        return executionQueryService.findUnified(
                projectId, type, teamId, area, iteration, Math.min(limit, 500));
    }
}
