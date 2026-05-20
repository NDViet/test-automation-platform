package com.platform.ingestion.management.dto;

import com.platform.core.domain.ProjectIntegrationConfig;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record IntegrationConfigDto(
        UUID id,
        UUID projectId,
        String integrationType,
        String displayName,
        String syncDirection,
        String repoType,
        Map<String, String> connectionParams,
        Map<String, Object> fieldMappings,
        Map<String, String> filterConfig,
        boolean enabled,
        Instant lastSyncedAt,
        int consecutiveErrors
) {
    private static final Set<String> SENSITIVE_KEYS = Set.of("token", "apikey", "secret", "password");

    public static IntegrationConfigDto from(ProjectIntegrationConfig c) {
        Map<String, String> maskedParams = null;
        if (c.getConnectionParams() != null) {
            maskedParams = c.getConnectionParams().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> isSensitive(e.getKey()) ? "***" : e.getValue()
                    ));
        }
        return new IntegrationConfigDto(
                c.getId(),
                c.getProjectId(),
                c.getIntegrationType(),
                c.getDisplayName(),
                c.getSyncDirection(),
                c.getRepoType(),
                maskedParams,
                c.getFieldMappings(),
                c.getFilterConfig(),
                c.isEnabled(),
                c.getLastSyncedAt(),
                c.getConsecutiveErrors()
        );
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }
}
