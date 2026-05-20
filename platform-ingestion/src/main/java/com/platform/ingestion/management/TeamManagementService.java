package com.platform.ingestion.management;

import com.platform.core.domain.Team;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.ingestion.management.dto.CreateTeamRequest;
import com.platform.ingestion.management.dto.UpdateTeamRequest;
import com.platform.ingestion.query.dto.TeamDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional
public class TeamManagementService {

    private final TeamRepository    teamRepo;
    private final ProjectRepository projectRepo;

    public TeamManagementService(TeamRepository teamRepo, ProjectRepository projectRepo) {
        this.teamRepo    = teamRepo;
        this.projectRepo = projectRepo;
    }

    public TeamDto createTeam(CreateTeamRequest req) {
        if (teamRepo.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already in use: " + req.slug());
        }
        return TeamDto.from(teamRepo.save(new Team(req.name(), req.slug())));
    }

    public TeamDto updateTeam(UUID id, UpdateTeamRequest req) {
        var team = teamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + id));
        if (req.name() != null && !req.name().isBlank()) {
            team.setName(req.name());
        }
        return TeamDto.from(teamRepo.save(team));
    }

    public void deleteTeam(UUID id) {
        var team = teamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + id));
        if (projectRepo.existsByTeamId(team.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Team still has projects. Delete or reassign them first.");
        }
        teamRepo.delete(team);
    }
}
