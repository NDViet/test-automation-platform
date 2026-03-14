package com.platform.core.repository;

import com.platform.core.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByTeamIdAndSlug(UUID teamId, String slug);
    Optional<Project> findBySlug(String slug);
    List<Project> findByTeamId(UUID teamId);
    boolean existsByTeamIdAndSlug(UUID teamId, String slug);
}
