package com.platform.ingestion.query;

import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.ingestion.query.dto.ProjectDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectQueryService {

    private final ProjectRepository projectRepo;
    private final TeamRepository teamRepo;

    public ProjectQueryService(ProjectRepository projectRepo, TeamRepository teamRepo) {
        this.projectRepo = projectRepo;
        this.teamRepo    = teamRepo;
    }

    public List<ProjectDto> findAll() {
        return projectRepo.findAll().stream().map(ProjectDto::from).toList();
    }

    public List<ProjectDto> findByTeamSlug(String teamSlug) {
        return teamRepo.findBySlug(teamSlug)
                .map(team -> projectRepo.findByTeamId(team.getId()).stream()
                        .map(ProjectDto::from).toList())
                .orElse(List.of());
    }

    public Optional<ProjectDto> findById(UUID id) {
        return projectRepo.findById(id).map(ProjectDto::from);
    }

    public Optional<ProjectDto> findBySlug(String slug) {
        return projectRepo.findAll().stream()
                .filter(p -> p.getSlug().equals(slug))
                .findFirst()
                .map(ProjectDto::from);
    }
}
