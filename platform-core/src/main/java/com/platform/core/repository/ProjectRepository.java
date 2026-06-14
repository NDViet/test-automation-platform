package com.platform.core.repository;

import com.platform.core.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByOrganizationIdAndSlug(UUID organizationId, String slug);
    Optional<Project> findBySlug(String slug);
    List<Project> findByOrganizationId(UUID organizationId);
    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);
    boolean existsByOrganizationId(UUID organizationId);
}
