package com.platform.core.repository;

import com.platform.core.domain.IntegrationSchemaSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSchemaSnapshotRepository
    extends JpaRepository<IntegrationSchemaSnapshot, UUID> {

  Optional<IntegrationSchemaSnapshot> findByProjectIdAndIntegrationTypeAndAdoProjectAndWorkItemType(
      UUID projectId, String integrationType, String adoProject, String workItemType);
}
