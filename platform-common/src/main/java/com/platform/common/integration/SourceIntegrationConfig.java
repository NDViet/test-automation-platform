package com.platform.common.integration;

import com.platform.common.model.Tier;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight DTO for an integration connection. Used in ContextBundle and IntegrationAdapter calls
 * — not the JPA entity. The full entity ({@code IntegrationConfigEntity}) lives in platform-core.
 */
public record SourceIntegrationConfig(
    UUID id,
    long projectId,
    Tier tier,
    IntegrationType integrationType,
    String displayName,
    SyncDirection syncDirection,
    Map<String, String> connectionParams, // base_url, project_key, workspace_id, etc.
    Map<String, Object> fieldMappings, // external field → platform field mapping
    Map<String, String> filterConfig, // JQL filter, suite IDs, path patterns, etc.
    boolean enabled) {
  /** Returns a connection parameter by key, or null if absent. */
  public String param(String key) {
    return connectionParams == null ? null : connectionParams.get(key);
  }

  /** Returns a field mapping target for the given external field name. */
  public Object mapping(String externalField) {
    return fieldMappings == null ? null : fieldMappings.get(externalField);
  }
}
