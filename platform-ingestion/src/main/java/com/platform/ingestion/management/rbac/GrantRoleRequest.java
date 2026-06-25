package com.platform.ingestion.management.rbac;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Grant a role to a user. {@code scope} = ORG | TEAM. For ORG, {@code teamId} must be null; for
 * TEAM it is required.
 */
public record GrantRoleRequest(
    @NotBlank String userId,
    @NotBlank String scope,
    UUID teamId,
    @NotBlank String role // ORG_ADMIN, TEAM_ADMIN, TEAM_MEMBER, VIEWER
    ) {}
