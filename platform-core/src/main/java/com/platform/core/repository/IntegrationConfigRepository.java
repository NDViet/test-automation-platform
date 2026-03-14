package com.platform.core.repository;

import com.platform.core.domain.IntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, UUID> {

    List<IntegrationConfig> findByTeamIdAndEnabledTrue(UUID teamId);

    Optional<IntegrationConfig> findByTeamIdAndTrackerType(UUID teamId, String trackerType);

    List<IntegrationConfig> findByEnabledTrue();
}
