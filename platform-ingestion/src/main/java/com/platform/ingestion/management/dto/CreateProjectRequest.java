package com.platform.ingestion.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateProjectRequest(
        @NotNull UUID orgId,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        String repoUrl
) {}
