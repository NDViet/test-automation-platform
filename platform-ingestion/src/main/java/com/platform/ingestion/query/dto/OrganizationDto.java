package com.platform.ingestion.query.dto;

import com.platform.core.domain.Organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDto(UUID id, String name, String slug, Instant createdAt,
                               String displayName, String logoUrl) {

    public static OrganizationDto from(Organization o) {
        String logoUrl = o.getLogoKey() != null
                ? "/api/v1/organizations/" + o.getId() + "/logo"
                : null;
        return new OrganizationDto(
                o.getId(),
                o.getName(),
                o.getSlug(),
                o.getCreatedAt(),
                o.getDisplayName(),
                logoUrl
        );
    }
}
