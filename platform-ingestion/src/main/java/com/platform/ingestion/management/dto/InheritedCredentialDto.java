package com.platform.ingestion.management.dto;

import com.platform.core.domain.IntegrationCredential;

import java.util.Map;

/**
 * A credential a project inherits from a higher scope (Organization / Team).
 * Surfaced read-only in Project → Settings → Integrations so a project sees what
 * it already gets for free and only overrides what it needs. Never carries the secret.
 *
 * @param integrationType  e.g. AZURE_DEVOPS_BOARDS, GITHUB, JIRA_CLOUD
 * @param scope            the scope that provides it (ORG or TEAM)
 * @param displayName      the credential's display name at that scope
 * @param baseUrl          inherited base URL (may be null)
 * @param connectionParams inherited non-secret params (org, project, owner, repo, …)
 * @param hasSecret        whether a secret (PAT/token) is configured at that scope
 */
public record InheritedCredentialDto(
        String integrationType,
        String scope,
        String displayName,
        String baseUrl,
        Map<String, String> connectionParams,
        boolean hasSecret
) {
    public static InheritedCredentialDto from(IntegrationCredential c) {
        boolean hasSecret = c.getSecretCiphertext() != null && !c.getSecretCiphertext().isBlank();
        return new InheritedCredentialDto(
                c.getIntegrationType(),
                c.getScope(),
                c.getDisplayName(),
                c.getBaseUrl(),
                c.getConnectionParams() != null ? c.getConnectionParams() : Map.of(),
                hasSecret
        );
    }
}
