package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.CreateTeamRequest;
import com.platform.ingestion.management.dto.UpdateTeamRequest;
import com.platform.ingestion.query.dto.TeamDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Team Management")
public class TeamManagementController {

    private final TeamManagementService service;

    public TeamManagementController(TeamManagementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody CreateTeamRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTeam(req));
    }

    @PutMapping("/{id}")
    public TeamDto updateTeam(@PathVariable UUID id, @RequestBody UpdateTeamRequest req) {
        return service.updateTeam(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable UUID id) {
        service.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }
}
