package com.platform.ingestion.query;

import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectRepository;
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
    private final OrganizationRepository orgRepo;

    public ProjectQueryService(ProjectRepository projectRepo, OrganizationRepository orgRepo) {
        this.projectRepo = projectRepo;
        this.orgRepo     = orgRepo;
    }

    public List<ProjectDto> findAll() {
        return projectRepo.findAll().stream().map(ProjectDto::from).toList();
    }

    public List<ProjectDto> findByOrgSlug(String orgSlug) {
        return orgRepo.findBySlug(orgSlug)
                .map(org -> projectRepo.findByOrganizationId(org.getId()).stream()
                        .map(ProjectDto::from).toList())
                .orElse(List.of());
    }

    public Optional<ProjectDto> findById(UUID id) {
        return projectRepo.findById(id).map(ProjectDto::from);
    }

    /** Resolve by org slug + project slug (the ADO-first slug pair). */
    public Optional<ProjectDto> findByOrgAndSlug(String orgSlug, String slug) {
        return orgRepo.findBySlug(orgSlug)
                .flatMap(org -> projectRepo.findByOrganizationIdAndSlug(org.getId(), slug))
                .map(ProjectDto::from);
    }

    public Optional<ProjectDto> findBySlug(String slug) {
        return projectRepo.findBySlug(slug).map(ProjectDto::from);
    }
}
