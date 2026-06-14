package com.platform.core.mapping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Config-driven heuristics for the Mapping Suggester and the sync-time
 * {@link MappingProfileApplier}. Loaded from {@code mapping-rules.json}
 * (overridable via {@code platform.mapping.rules-path}) and per-scope overrides,
 * so org-/process-specific keywords, field targets, value maps and formulas live in
 * configuration rather than hardcoded in source.
 *
 * <p>{@code appliesTo} (on field/value rules) is one of {@code ANY} (default),
 * {@code DEFECT} or {@code REQUIREMENT} — restricting a rule to that lane.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MappingRules(
        String apiVersion,
        String kind,
        String defaultLane,
        String defaultIssueType,
        List<String> nonTracked,
        List<LaneRule> laneRules,
        List<FieldRule> standardFields,
        List<FieldRule> fieldHeuristics,
        Map<String, String> statusCategoryMap,
        Map<String, String> stateCategoryByName,
        /** Category for states not in {@code stateCategoryByName} (exclude strategy: known
         *  backlog/terminal states are mapped explicitly, everything else falls here —
         *  typically {@code InProgress}). Null = leave unmapped states unresolved. */
        String defaultStateCategory,
        String blockedStateName,
        String blockedStateOverride,
        ValueRule severity,
        ValueRule priority,
        List<DefectRule> defectRules,
        Map<String, String> defectFormulas
) {
    /** Routes a work-item type name (by keyword) to a lane + optional issueType. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LaneRule(List<String> keywords, String lane, String issueType) {}

    /**
     * Maps a field to a canonical target. Match either by exact {@code referenceName}
     * (standard fields) or by {@code keywords} found in "referenceName name" (heuristics).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldRule(List<String> keywords, String referenceName, String target, String appliesTo) {}

    /** A value-map rule (status/severity/priority): read {@code field}, optionally transform/map, with a default. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValueRule(
            String field,
            String transform,
            Map<String, String> map,
            @JsonProperty("default") String defaultValue,
            String appliesTo) {}

    /**
     * A defect rule template. When a field whose "referenceName name" contains
     * {@code whenFieldKeyword} exists, emit a rule with {@code whenExpr}
     * ({@code {ref}} → that field's referenceName) and {@code set}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DefectRule(String whenFieldKeyword, String whenExpr, Map<String, Object> set) {}
}
