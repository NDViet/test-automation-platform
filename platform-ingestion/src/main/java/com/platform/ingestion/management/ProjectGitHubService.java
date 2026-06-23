package com.platform.ingestion.management;

import com.platform.core.domain.GitHubRepoCache;
import com.platform.core.domain.ProjectRepoAssignment;
import com.platform.core.repository.GitHubRepoCacheRepository;
import com.platform.core.repository.ProjectRepoAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectGitHubService {

    private final ProjectRepoAssignmentRepository assignmentRepo;
    private final GitHubRepoCacheRepository cacheRepo;

    public ProjectGitHubService(ProjectRepoAssignmentRepository assignmentRepo,
                                GitHubRepoCacheRepository cacheRepo) {
        this.assignmentRepo = assignmentRepo;
        this.cacheRepo      = cacheRepo;
    }

    public record AssignmentDto(
            String id,
            String repoFullName,
            String role,
            String credentialId,
            String owner,
            String name,
            String htmlUrl,
            boolean isPrivate) {}

    public record SaveDto(String repoFullName, String role, String credentialId) {}

    public List<AssignmentDto> getAssignments(UUID projectId) {
        List<ProjectRepoAssignment> assignments =
                assignmentRepo.findByProjectIdOrderByRepoFullName(projectId);
        if (assignments.isEmpty()) return List.of();

        // Batch-load cache metadata for all distinct credentials in this project
        Set<UUID> credIds = assignments.stream()
                .map(ProjectRepoAssignment::getCredentialId)
                .collect(Collectors.toSet());
        Map<String, GitHubRepoCache> cacheMap = new HashMap<>();
        for (UUID credId : credIds) {
            for (GitHubRepoCache c : cacheRepo.findByCredentialIdOrderByFullName(credId)) {
                cacheMap.put(c.getCredentialId() + ":" + c.getFullName(), c);
            }
        }

        return assignments.stream().map(a -> {
            GitHubRepoCache c = cacheMap.get(a.getCredentialId() + ":" + a.getRepoFullName());
            String owner = null, name = null, htmlUrl = null;
            boolean isPrivate = false;
            if (c != null) {
                owner = c.getOwner(); name = c.getRepoName();
                htmlUrl = c.getHtmlUrl(); isPrivate = c.isPrivate();
            } else if (a.getRepoFullName().contains("/")) {
                String[] parts = a.getRepoFullName().split("/", 2);
                owner = parts[0]; name = parts[1];
            }
            return new AssignmentDto(a.getId().toString(), a.getRepoFullName(), a.getRole(),
                    a.getCredentialId().toString(), owner, name, htmlUrl, isPrivate);
        }).toList();
    }

    @Transactional
    public List<AssignmentDto> setAssignments(UUID projectId, List<SaveDto> dtos) {
        assignmentRepo.deleteByProjectId(projectId);
        if (dtos != null) {
            List<ProjectRepoAssignment> toSave = new ArrayList<>();
            for (SaveDto dto : dtos) {
                if (dto.repoFullName() == null || dto.repoFullName().isBlank()) continue;
                if (dto.credentialId() == null || dto.credentialId().isBlank()) continue;
                toSave.add(new ProjectRepoAssignment(
                        projectId,
                        UUID.fromString(dto.credentialId()),
                        dto.repoFullName(),
                        dto.role()));
            }
            assignmentRepo.saveAll(toSave);
        }
        return getAssignments(projectId);
    }
}
