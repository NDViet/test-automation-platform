package com.platform.ingestion.management;

import com.platform.core.domain.Project;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.ingestion.management.dto.CreateProjectRequest;
import com.platform.ingestion.management.dto.UpdateProjectRequest;
import com.platform.ingestion.query.dto.ProjectDto;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProjectManagementService {

  private final ProjectRepository projectRepo;
  private final OrganizationRepository orgRepo;

  public ProjectManagementService(ProjectRepository projectRepo, OrganizationRepository orgRepo) {
    this.projectRepo = projectRepo;
    this.orgRepo = orgRepo;
  }

  public ProjectDto createProject(CreateProjectRequest req) {
    var org =
        orgRepo
            .findById(req.orgId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organization not found: " + req.orgId()));
    var project = new Project(org, req.name(), req.slug());
    if (req.repoUrl() != null) {
      project.setRepoUrl(req.repoUrl());
    }
    return ProjectDto.from(projectRepo.save(project));
  }

  public ProjectDto updateProject(UUID id, UpdateProjectRequest req) {
    var project =
        projectRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id));
    if (req.name() != null) {
      project.setName(req.name());
    }
    if (req.repoUrl() != null) {
      project.setRepoUrl(req.repoUrl());
    }
    return ProjectDto.from(projectRepo.save(project));
  }

  public void deleteProject(UUID id) {
    var project =
        projectRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id));
    projectRepo.delete(project);
  }
}
