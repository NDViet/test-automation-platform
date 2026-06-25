package com.platform.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.domain.IntegrationCredential.Scope;
import com.platform.core.domain.Project;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.repository.ProjectRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the effective integration credential by deep-merging the scoped credentials along the
 * ADO-first hierarchy <b>Organization → Project → Team</b>.
 *
 * <p>Precedence (most specific wins): <b>TEAM &gt; PROJECT &gt; ORG</b>. The ORG scope is keyed by
 * the project's organization id. Connection params merge key-by-key; the secret is taken whole from
 * the most specific scope that defines one.
 */
@Service
public class CredentialResolver {

  private static final Logger log = LoggerFactory.getLogger(CredentialResolver.class);
  private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

  private final IntegrationCredentialRepository repo;
  private final ProjectRepository projectRepo;
  private final CredentialCipher cipher;
  private final ObjectMapper objectMapper;

  public CredentialResolver(
      IntegrationCredentialRepository repo,
      ProjectRepository projectRepo,
      CredentialCipher cipher,
      ObjectMapper objectMapper) {
    this.repo = repo;
    this.projectRepo = projectRepo;
    this.cipher = cipher;
    this.objectMapper = objectMapper;
  }

  /**
   * Resolves the effective credential for a project (Org → Project precedence). Returns empty if no
   * enabled credential exists at either scope.
   */
  @Transactional(readOnly = true)
  public Optional<ResolvedCredential> resolve(UUID projectId, String integrationType) {
    return resolve(projectId, null, integrationType);
  }

  /** Resolves with an optional team override (Org → Project → Team precedence). */
  @Transactional(readOnly = true)
  public Optional<ResolvedCredential> resolve(UUID projectId, UUID teamId, String integrationType) {
    UUID orgId =
        projectRepo
            .findById(projectId)
            .map(Project::getOrganization)
            .map(o -> o.getId())
            .orElse(null);

    // Build the cascade ORG → PROJECT → TEAM (most specific last; last wins).
    List<IntegrationCredential> cascade = new java.util.ArrayList<>();
    if (orgId != null) scoped(Scope.ORG, orgId, integrationType).ifPresent(cascade::add);
    scoped(Scope.PROJECT, projectId, integrationType).ifPresent(cascade::add);
    if (teamId != null) scoped(Scope.TEAM, teamId, integrationType).ifPresent(cascade::add);

    if (cascade.isEmpty()) return Optional.empty();

    Map<String, String> params = new LinkedHashMap<>();
    String baseUrl = null;
    Map<String, String> secret = null;
    Scope secretScope = null;
    for (IntegrationCredential c : cascade) {
      if (c.getConnectionParams() != null) params.putAll(c.getConnectionParams());
      if (c.getBaseUrl() != null && !c.getBaseUrl().isBlank()) baseUrl = c.getBaseUrl();
      if (c.getSecretCiphertext() != null && !c.getSecretCiphertext().isBlank()) {
        secret = decryptSecret(c);
        secretScope = Scope.valueOf(c.getScope());
      }
    }
    return Optional.of(
        new ResolvedCredential(integrationType, baseUrl, params, secret, secretScope));
  }

  private Optional<IntegrationCredential> scoped(Scope scope, UUID scopeId, String type) {
    return repo.findByScopeAndScopeIdAndIntegrationType(scope.name(), scopeId, type)
        .filter(IntegrationCredential::isEnabled);
  }

  private Map<String, String> decryptSecret(IntegrationCredential c) {
    try {
      String json = cipher.decrypt(c.getSecretCiphertext());
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception e) {
      log.error(
          "[CredentialResolver] Failed to decrypt secret for credential {} ({} {}): {}",
          c.getId(),
          c.getScope(),
          c.getIntegrationType(),
          e.getMessage());
      return Map.of();
    }
  }
}
