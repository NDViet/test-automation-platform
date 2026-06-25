package com.platform.core.repository;

import com.platform.core.domain.IntegrationCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationCredentialRepository
    extends JpaRepository<IntegrationCredential, UUID> {

  /** ORG-scoped credential (scope_id IS NULL) for a given integration type. */
  Optional<IntegrationCredential> findByScopeAndScopeIdIsNullAndIntegrationType(
      String scope, String integrationType);

  /** TEAM- or PROJECT-scoped credential for a given owner id and integration type. */
  Optional<IntegrationCredential> findByScopeAndScopeIdAndIntegrationType(
      String scope, UUID scopeId, String integrationType);

  /** All enabled credentials for an integration type, any scope (used by sync schedulers). */
  List<IntegrationCredential> findByIntegrationTypeAndEnabledTrue(String integrationType);

  /** All ORG-scoped credentials (admin listing). */
  List<IntegrationCredential> findByScopeAndScopeIdIsNull(String scope);

  /** All credentials owned by a team or project (admin/project settings listing). */
  List<IntegrationCredential> findByScopeAndScopeId(String scope, UUID scopeId);

  /** GitHub credentials that have a periodic sync interval configured (for scheduler). */
  List<IntegrationCredential> findByIntegrationTypeAndSyncIntervalMinutesGreaterThan(
      String integrationType, int threshold);
}
