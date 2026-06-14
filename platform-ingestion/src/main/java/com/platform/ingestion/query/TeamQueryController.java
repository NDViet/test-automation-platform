package com.platform.ingestion.query;

import com.platform.ingestion.query.dto.TeamDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/teams")
@Tag(name = "Teams", description = "Teams within a project (ADO-first: Org → Project → Team)")
public class TeamQueryController {

    private final TeamQueryService teamQueryService;

    public TeamQueryController(TeamQueryService teamQueryService) {
        this.teamQueryService = teamQueryService;
    }

    @GetMapping
    @Operation(summary = "List teams within a project")
    public List<TeamDto> listByProject(@PathVariable UUID projectId) {
        return teamQueryService.findByProject(projectId);
    }
}
