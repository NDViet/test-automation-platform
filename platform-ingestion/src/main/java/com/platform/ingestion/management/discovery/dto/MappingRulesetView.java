package com.platform.ingestion.management.discovery.dto;

import java.time.Instant;

/**
 * The mapping ruleset for a scope, for the portal editor.
 *
 * @param scope       ORG or PROJECT
 * @param customized  whether this scope has its own saved override (false = inheriting)
 * @param source      where the shown rules currently come from: PROJECT, ORG or DEFAULT
 * @param json        the rules document to edit (this scope's override if customized, else the inherited/default)
 * @param updatedBy   who last saved the override (null when inheriting)
 * @param updatedAt   when the override was last saved (null when inheriting)
 */
public record MappingRulesetView(
        String scope,
        boolean customized,
        String source,
        String json,
        String updatedBy,
        Instant updatedAt
) {}
