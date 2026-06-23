package com.platform.ingestion.management;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Project-level GitHub repo assignments (which repos a project uses, with per-repo role). */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Project GitHub Repos")
public class ProjectGitHubController {

    private final ProjectGitHubService service;

    public ProjectGitHubController(ProjectGitHubService service) {
        this.service = service;
    }

    @GetMapping("/{projectId}/github/repos")
    @Operation(summary = "List repos assigned to a project (enriched with cached metadata)")
    public List<ProjectGitHubService.AssignmentDto> list(@PathVariable UUID projectId) {
        return service.getAssignments(projectId);
    }

    public record SetRequest(List<ProjectGitHubService.SaveDto> assignments) {}

    @PutMapping("/{projectId}/github/repos")
    @Operation(summary = "Replace all repo assignments for a project")
    public List<ProjectGitHubService.AssignmentDto> set(
            @PathVariable UUID projectId,
            @RequestBody SetRequest req) {
        return service.setAssignments(projectId, req.assignments());
    }
}
