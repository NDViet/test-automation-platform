package com.platform.ingestion.query.dto;

import java.util.List;

/** A page of requirements for server-side pagination. */
public record PagedRequirementsDto(
        List<RequirementDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
