package com.platform.core.mapping;

import com.platform.core.mapping.MappingRules.LaneRule;
import com.platform.core.mapping.MappingRules.ValueRule;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Applies a resolved {@link MappingRules} profile to an upstream work item at sync time,
 * producing the canonical values the platform persists (status, priority). Pure /
 * deterministic. Status maps via the configured state-name → category → canonical chain,
 * which is drift-resilient (state renames within a category don't change the result).
 *
 * <p>Unmapped/custom fields are intentionally NOT collapsed here — the sync stores the
 * full raw upstream payload, so nothing is lost when the schema drifts.</p>
 */
@Component
public class MappingProfileApplier {

    /** A canonical value plus whether the profile could resolve it (so callers can avoid null-overwrite). */
    public record Resolved(String value, boolean resolved) {
        public static Resolved unresolved() { return new Resolved(null, false); }
        public static Resolved of(String v) { return new Resolved(v, v != null); }
    }

    /**
     * Canonical status for an upstream state name (e.g. ADO {@code System.State}).
     * Chain: blocked override → stateName→category → category→canonical. Unresolved if
     * the state is unknown, so the caller keeps the last-known status (no clobber).
     */
    public Resolved status(MappingRules rules, String stateName) {
        if (rules == null || stateName == null || stateName.isBlank()) return Resolved.unresolved();
        String s = stateName.trim();

        String blockedName = rules.blockedStateName();
        if (blockedName != null && blockedName.equalsIgnoreCase(s) && rules.blockedStateOverride() != null) {
            return Resolved.of(rules.blockedStateOverride());
        }
        String category = lookupIgnoreCase(rules.stateCategoryByName(), s);
        if (category == null) category = rules.defaultStateCategory();   // exclude strategy: unknown → in-progress
        if (category == null) return Resolved.unresolved();
        String canonical = lookupIgnoreCase(rules.statusCategoryMap(), category);
        return canonical != null ? Resolved.of(canonical) : Resolved.unresolved();
    }

    /**
     * Canonical priority from the configured priority value-rule applied to the upstream
     * field value (e.g. {@code Microsoft.VSTS.Common.Priority} = "2" → "P2").
     */
    public Resolved priority(MappingRules rules, Map<String, String> upstreamFields) {
        if (rules == null || rules.priority() == null || upstreamFields == null) return Resolved.unresolved();
        ValueRule pr = rules.priority();
        if (pr.field() == null) return Resolved.unresolved();
        String raw = upstreamFields.get(pr.field());
        if (raw == null || raw.isBlank()) {
            return pr.defaultValue() != null ? Resolved.of(pr.defaultValue()) : Resolved.unresolved();
        }
        String mapped = pr.map() != null ? pr.map().get(raw.trim()) : null;
        if (mapped != null) return Resolved.of(mapped);
        return pr.defaultValue() != null ? Resolved.of(pr.defaultValue()) : Resolved.unresolved();
    }

    /**
     * Canonical issue type for an upstream work-item-type name, derived from the profile's
     * lane rules (e.g. "Acme Bug" → DEFECT, "Acme Team Epic" → EPIC, "Acme Capability" →
     * CAPABILITY). Returns null for non-tracked types (caller falls back to its own
     * normalization). DEFECT lane → "DEFECT"; requirement lanes → their configured issueType.
     */
    public String issueType(MappingRules rules, String workItemType) {
        if (rules == null || workItemType == null || workItemType.isBlank()) return null;
        String n = workItemType.toLowerCase();
        if (keywordsMatch(rules.nonTracked(), n)) return null;   // not tracked → caller decides
        for (LaneRule lr : safe(rules.laneRules())) {
            if (keywordsMatch(lr.keywords(), n)) {
                if ("DEFECT".equals(lr.lane())) return "DEFECT";
                return lr.issueType();                            // EPIC / CAPABILITY / FEATURE / STORY / …
            }
        }
        return rules.defaultIssueType();
    }

    private static boolean keywordsMatch(List<String> keywords, String lowerHaystack) {
        return keywords != null && keywords.stream().anyMatch(k -> lowerHaystack.contains(k.toLowerCase()));
    }

    private static <T> List<T> safe(List<T> l) {
        return l == null ? List.of() : l;
    }

    private static String lookupIgnoreCase(Map<String, String> map, String key) {
        if (map == null || key == null) return null;
        String exact = map.get(key);
        if (exact != null) return exact;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }
}
