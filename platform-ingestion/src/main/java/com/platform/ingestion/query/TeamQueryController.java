package com.platform.ingestion.query;

import com.platform.ingestion.query.dto.TeamDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Team management queries")
public class TeamQueryController {

    private final TeamQueryService teamQueryService;

    public TeamQueryController(TeamQueryService teamQueryService) {
        this.teamQueryService = teamQueryService;
    }

    @GetMapping
    @Operation(summary = "List all teams")
    public List<TeamDto> listAll() {
        return teamQueryService.findAll();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get team by slug")
    public ResponseEntity<TeamDto> getBySlug(@PathVariable String slug) {
        return teamQueryService.findBySlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
