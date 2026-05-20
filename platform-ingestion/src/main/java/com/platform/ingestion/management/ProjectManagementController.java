package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.CreateProjectRequest;
import com.platform.ingestion.management.dto.UpdateProjectRequest;
import com.platform.ingestion.query.dto.ProjectDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Project Management")
public class ProjectManagementController {

    private final ProjectManagementService service;

    public ProjectManagementController(ProjectManagementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@Valid @RequestBody CreateProjectRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProject(req));
    }

    @PutMapping("/{id}")
    public ProjectDto updateProject(@PathVariable UUID id, @RequestBody UpdateProjectRequest req) {
        return service.updateProject(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        service.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
