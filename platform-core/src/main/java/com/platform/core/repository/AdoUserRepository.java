package com.platform.core.repository;

import com.platform.core.domain.AdoUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdoUserRepository extends JpaRepository<AdoUser, UUID> {
    List<AdoUser> findByProjectIdOrderByDisplayName(UUID projectId);
    Optional<AdoUser> findByProjectIdAndUniqueName(UUID projectId, String uniqueName);
    long countByProjectId(UUID projectId);
    long countByProjectIdAndQualityRoleIsNotNull(UUID projectId);
}
