package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.CreateTeamRequest;
import com.platform.ingestion.management.dto.UpdateTeamRequest;
import com.platform.ingestion.query.dto.TeamDto;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/teams")
@Tag(
    name = "Team Management",
    description = "Teams within a project (ADO-first: Org → Project → Team)")
@RequireCapability(value = Capability.MANAGE_PROJECT, scope = "projectId")
public class TeamManagementController {

  private final TeamManagementService service;

  public TeamManagementController(TeamManagementService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<TeamDto> createTeam(
      @PathVariable UUID projectId, @Valid @RequestBody CreateTeamRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createTeam(projectId, req));
  }

  @PutMapping("/{id}")
  public TeamDto updateTeam(
      @PathVariable UUID projectId, @PathVariable UUID id, @RequestBody UpdateTeamRequest req) {
    return service.updateTeam(projectId, id, req);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteTeam(@PathVariable UUID projectId, @PathVariable UUID id) {
    service.deleteTeam(projectId, id);
    return ResponseEntity.noContent().build();
  }
}
