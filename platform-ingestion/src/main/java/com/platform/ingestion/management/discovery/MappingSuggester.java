package com.platform.ingestion.management.discovery;

import com.platform.core.mapping.MappingRules;
import com.platform.core.mapping.MappingRules.DefectRule;
import com.platform.core.mapping.MappingRules.FieldRule;
import com.platform.core.mapping.MappingRules.LaneRule;
import com.platform.core.mapping.MappingRules.ValueRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Heuristically suggests a mapping profile (the §3 schema in
 * docs/REQUIREMENTS_DEFECTS_MAPPING_STRATEGY.md) from a discovered ADO work-item type: its fields
 * and states. Pure / deterministic — unit-testable, no network.
 *
 * <p>All heuristics (lane keywords, field targets, value maps, formulas) are config-driven via
 * {@link MappingRules} / {@link MappingRulesProvider}; nothing org- or process-specific is
 * hardcoded here.
 */
@Component
public class MappingSuggester {

  public record Lane(String lane, String issueType) {}

  /** Routes a work-item type name to a canonical lane + issueType (config-driven). */
  public Lane suggestLane(MappingRules r, String typeName) {
    String n = typeName.toLowerCase();
    if (anyContains(n, r.nonTracked())) return new Lane("IGNORE", null);
    for (LaneRule lr : safe(r.laneRules())) {
      if (keywordsMatch(lr.keywords(), n)) return new Lane(lr.lane(), lr.issueType());
    }
    return new Lane(orElse(r.defaultLane(), "REQUIREMENT"), r.defaultIssueType());
  }

  /**
   * Builds a suggested profile as a JSON-friendly ordered map.
   *
   * @param integrationType e.g. AZURE_DEVOPS_BOARDS
   * @param typeName ADO work-item type
   * @param fields available fields (referenceName + display name)
   * @param stateCategories distinct state categories present
   *     (Proposed/InProgress/Completed/Removed)
   * @param hasBlockedState whether a state literally named "Blocked" exists
   */
  public Map<String, Object> suggest(
      MappingRules r,
      String integrationType,
      String typeName,
      List<Field> fields,
      List<String> stateCategories,
      boolean hasBlockedState) {
    Lane lane = suggestLane(r, typeName);
    boolean defect = "DEFECT".equals(lane.lane());

    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("lane", lane.lane());
    if (lane.issueType() != null) spec.put("issueType", lane.issueType());

    // ── field map ──
    Map<String, String> fieldMap = new LinkedHashMap<>();
    for (FieldRule sf : safe(r.standardFields())) {
      if (!appliesTo(sf.appliesTo(), defect)) continue;
      if (sf.referenceName() != null && has(fields, sf.referenceName())) {
        fieldMap.put(sf.referenceName(), sf.target());
      }
    }
    for (Field f : fields) {
      String hay = (f.referenceName() + " " + f.name()).toLowerCase();
      for (FieldRule fh : safe(r.fieldHeuristics())) {
        if (!appliesTo(fh.appliesTo(), defect)) continue;
        if (keywordsMatch(fh.keywords(), hay)) {
          fieldMap.put(f.referenceName(), fh.target());
          break; // first matching heuristic wins for this field
        }
      }
    }
    spec.put("fieldMap", fieldMap);

    // ── value map ──
    Map<String, Object> valueMap = new LinkedHashMap<>();
    if (r.statusCategoryMap() != null) {
      Map<String, Object> status = new LinkedHashMap<>();
      status.put("by", "stateCategory");
      Map<String, String> catMap = new LinkedHashMap<>();
      r.statusCategoryMap()
          .forEach(
              (cat, mapped) -> {
                if (stateCategories.contains(cat)) catMap.put(cat, mapped);
              });
      status.put("map", catMap);
      if (hasBlockedState && r.blockedStateOverride() != null) {
        status.put(
            "overrides", Map.of(orElse(r.blockedStateName(), "Blocked"), r.blockedStateOverride()));
      }
      valueMap.put("status", status);
    }
    putValueRule(valueMap, "severity", r.severity(), defect, fields);
    putValueRule(valueMap, "priority", r.priority(), defect, fields);
    spec.put("valueMap", valueMap);

    // ── rules + formulas (defect quality signals) ──
    if (defect) {
      List<Map<String, Object>> rules = new ArrayList<>();
      for (DefectRule dr : safe(r.defectRules())) {
        if (dr.whenFieldKeyword() == null) continue;
        String kw = dr.whenFieldKeyword().toLowerCase();
        fields.stream()
            .filter(f -> (f.referenceName() + " " + f.name()).toLowerCase().contains(kw))
            .findFirst()
            .ifPresent(
                f -> {
                  Map<String, Object> rm = new LinkedHashMap<>();
                  if (dr.whenExpr() != null)
                    rm.put("when", dr.whenExpr().replace("{ref}", f.referenceName()));
                  rm.put("set", dr.set() != null ? dr.set() : Map.of());
                  rules.add(rm);
                });
      }
      if (!rules.isEmpty()) spec.put("rules", rules);
      if (r.defectFormulas() != null && !r.defectFormulas().isEmpty()) {
        spec.put("formulas", new LinkedHashMap<>(r.defectFormulas()));
      }
    }

    // ── envelope ──
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("apiVersion", orElse(r.apiVersion(), "quality.platform/v1"));
    profile.put("kind", orElse(r.kind(), "MappingProfile"));
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("name", slug(typeName) + "-" + lane.lane().toLowerCase());
    meta.put("integrationType", integrationType);
    meta.put("workItemType", typeName);
    profile.put("metadata", meta);
    profile.put("spec", spec);
    return profile;
  }

  // ── helpers ──
  public record Field(String referenceName, String name, boolean custom, boolean required) {}

  private static void putValueRule(
      Map<String, Object> valueMap,
      String key,
      ValueRule rule,
      boolean defect,
      List<Field> fields) {
    if (rule == null || !appliesTo(rule.appliesTo(), defect)) return;
    if (rule.field() == null || !has(fields, rule.field())) return;
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("by", rule.field());
    if (rule.transform() != null) m.put("transform", rule.transform());
    if (rule.map() != null && !rule.map().isEmpty()) m.put("map", new LinkedHashMap<>(rule.map()));
    if (rule.defaultValue() != null) m.put("default", rule.defaultValue());
    valueMap.put(key, m);
  }

  /** ANY/blank → both lanes; DEFECT → defect only; REQUIREMENT → non-defect only. */
  private static boolean appliesTo(String appliesTo, boolean defect) {
    if (appliesTo == null || appliesTo.isBlank()) return true;
    return switch (appliesTo.toUpperCase()) {
      case "DEFECT" -> defect;
      case "REQUIREMENT" -> !defect;
      default -> true; // ANY / BOTH / unknown
    };
  }

  private static boolean keywordsMatch(List<String> keywords, String lowerHaystack) {
    return keywords != null
        && keywords.stream().anyMatch(k -> lowerHaystack.contains(k.toLowerCase()));
  }

  private static boolean anyContains(String lowerHaystack, List<String> needles) {
    return keywordsMatch(needles, lowerHaystack);
  }

  private static boolean has(List<Field> fields, String ref) {
    return fields.stream().anyMatch(f -> f.referenceName().equals(ref));
  }

  private static <T> List<T> safe(List<T> l) {
    return l == null ? List.of() : l;
  }

  private static String orElse(String v, String fallback) {
    return (v == null || v.isBlank()) ? fallback : v;
  }

  private static String slug(String s) {
    return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }
}
