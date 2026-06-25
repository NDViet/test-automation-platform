package com.platform.ingestion.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateOrganizationRequest(
    @NotBlank String name, @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug) {}
