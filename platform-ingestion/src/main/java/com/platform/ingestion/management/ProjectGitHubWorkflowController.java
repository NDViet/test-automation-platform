package com.platform.ingestion.management;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Read + trigger GitHub Actions workflows for TEST_AUTOMATION repos in a project. */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Project GitHub Workflows")
public class ProjectGitHubWorkflowController {

  private final ProjectGitHubWorkflowService service;

  public ProjectGitHubWorkflowController(ProjectGitHubWorkflowService service) {
    this.service = service;
  }

  @GetMapping("/{projectId}/github/workflows")
  @Operation(summary = "List all GitHub Actions workflows across TEST_AUTOMATION repos")
  public List<ProjectGitHubWorkflowService.WorkflowDto> workflows(@PathVariable UUID projectId) {
    return service.listWorkflows(projectId);
  }

  @GetMapping("/{projectId}/github/workflow-runs")
  @Operation(summary = "Recent runs for a specific workflow")
  public List<ProjectGitHubWorkflowService.RunDto> runs(
      @PathVariable UUID projectId,
      @RequestParam String repo,
      @RequestParam long workflowId,
      @RequestParam(defaultValue = "15") int limit) {
    return service.listRuns(projectId, repo, workflowId, limit);
  }

  @PostMapping("/{projectId}/github/workflow-dispatch")
  @Operation(summary = "Trigger a workflow_dispatch event")
  public ProjectGitHubWorkflowService.DispatchResult dispatch(
      @PathVariable UUID projectId, @RequestBody ProjectGitHubWorkflowService.DispatchRequest req) {
    return service.triggerDispatch(projectId, req);
  }
}
