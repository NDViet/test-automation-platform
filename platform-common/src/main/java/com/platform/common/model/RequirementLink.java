package com.platform.common.model;

import java.util.UUID;

/** A cross-requirement relationship as seen from one requirement's perspective. */
public record RequirementLink(
        UUID targetId,
        String targetExternalId,
        String targetTitle,
        EdgeType edgeType,
        LinkSubtype linkSubtype
) {}
