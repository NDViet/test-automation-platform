package com.platform.core.repository;

import com.platform.core.domain.ProjectIntegrationConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectIntegrationConfigRepository
    extends JpaRepository<ProjectIntegrationConfig, UUID> {
  List<ProjectIntegrationConfig> findByProjectId(UUID projectId);

  List<ProjectIntegrationConfig> findByProjectIdAndEnabled(UUID projectId, boolean enabled);

  List<ProjectIntegrationConfig> findByProjectIdAndTierAndEnabled(
      UUID projectId, String tier, boolean enabled);

  Optional<ProjectIntegrationConfig> findByProjectIdAndIntegrationType(
      UUID projectId, String integrationType);

  List<ProjectIntegrationConfig> findAllByProjectIdAndIntegrationType(
      UUID projectId, String integrationType);

  List<ProjectIntegrationConfig> findByIntegrationTypeAndEnabled(
      String integrationType, boolean enabled);

  List<ProjectIntegrationConfig> findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
      UUID projectId, String integrationType, String repoType, boolean enabled);
}
