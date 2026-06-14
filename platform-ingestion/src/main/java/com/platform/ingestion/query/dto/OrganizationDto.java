package com.platform.ingestion.query.dto;

import com.platform.core.domain.Organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDto(UUID id, String name, String slug, Instant createdAt) {
    public static OrganizationDto from(Organization o) {
        return new OrganizationDto(o.getId(), o.getName(), o.getSlug(), o.getCreatedAt());
    }
}
