package com.platform.ingestion.management.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;

/**
 * Create/update request for a scoped integration credential.
 *
 * <p>{@code scope} = ORG | TEAM | PROJECT. {@code scopeId} must be null for ORG and
 * non-null otherwise. {@code secret} is write-only (e.g. {@code {"pat":"..."}});
 * omit it on update to keep the existing secret.</p>
 */
public record SaveCredentialRequest(
        @NotBlank String scope,
        UUID scopeId,
        @NotBlank String integrationType,
        @NotBlank String displayName,
        String baseUrl,
        Map<String, String> connectionParams,
        Map<String, String> secret,
        Boolean enabled
) {}
