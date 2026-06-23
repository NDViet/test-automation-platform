package com.platform.ingestion.management.dto;

import com.platform.core.domain.IntegrationCredential;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read view of an {@link IntegrationCredential}. Secrets are never returned —
 * only {@code hasSecret} indicates whether one is stored.
 */
public record CredentialDto(
        UUID id,
        String scope,
        UUID scopeId,
        String integrationType,
        String displayName,
        String baseUrl,
        Map<String, String> connectionParams,   // non-secret only
        boolean hasSecret,
        boolean enabled,
        int syncIntervalMinutes,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static CredentialDto from(IntegrationCredential c) {
        return new CredentialDto(
                c.getId(), c.getScope(), c.getScopeId(), c.getIntegrationType(),
                c.getDisplayName(), c.getBaseUrl(), c.getConnectionParams(),
                c.getSecretCiphertext() != null && !c.getSecretCiphertext().isBlank(),
                c.isEnabled(), c.getSyncIntervalMinutes(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
