package com.platform.ingestion.management.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

public record SaveIntegrationConfigRequest(
    UUID id,
    @NotBlank String integrationType,
    String displayName,
    String syncDirection,
    String repoType,
    Map<String, String> connectionParams,
    Map<String, Object> fieldMappings,
    Map<String, String> filterConfig,
    boolean enabled) {}
