package com.platform.ingestion.management.discovery.dto;

import java.time.Instant;
import java.util.List;

/**
 * Drift between the live upstream schema and the captured baseline for a work-item type.
 *
 * @param hasBaseline false the first time (a baseline was just captured)
 * @param justCaptured true when this call created the baseline
 * @param hasDrift any removed/added/type-changed field or state-category change
 * @param removed fields in the baseline but no longer present (mapped=was mapped → high risk)
 * @param added new fields not in the baseline (mapped=suggester would auto-map it)
 * @param typeChanged fields whose data type changed
 */
public record SchemaDriftReport(
    String workItemType,
    boolean hasBaseline,
    boolean justCaptured,
    boolean hasDrift,
    Instant baselineCapturedAt,
    List<FieldChange> removed,
    List<FieldChange> added,
    List<TypeChange> typeChanged,
    List<String> removedStateCategories,
    List<String> addedStateCategories) {
  public record FieldChange(String referenceName, String name, String type, boolean mapped) {}

  public record TypeChange(String referenceName, String name, String fromType, String toType) {}
}
