package com.platform.ingestion.query;

import com.platform.ingestion.query.dto.ProjectDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Project management queries")
public class ProjectQueryController {

    private final ProjectQueryService projectQueryService;

    public ProjectQueryController(ProjectQueryService projectQueryService) {
        this.projectQueryService = projectQueryService;
    }

    @GetMapping
    @Operation(summary = "List all projects, optionally filtered by team slug")
    public List<ProjectDto> list(@RequestParam(required = false) String teamSlug) {
        return teamSlug != null
                ? projectQueryService.findByTeamSlug(teamSlug)
                : projectQueryService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ProjectDto> getById(@PathVariable UUID id) {
        return projectQueryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-slug/{slug}")
    @Operation(summary = "Get project by slug")
    public ResponseEntity<ProjectDto> getBySlug(@PathVariable String slug) {
        return projectQueryService.findBySlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
