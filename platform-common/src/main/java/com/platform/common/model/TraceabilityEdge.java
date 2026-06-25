package com.platform.common.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A typed, directional relationship between two nodes in the five-tier traceability graph.
 * Persisted in {@code platform_traceability_edges}.
 */
public record TraceabilityEdge(
    UUID fromId,
    Tier fromTier,
    UUID toId,
    Tier toTier,
    EdgeType edgeType,
    LinkSubtype linkSubtype, // non-null only for LINKED_TO and PARENT_OF edges
    double confidence, // 1.0 = explicit; < 1.0 = AI-inferred
    Map<String, Object> metadata,
    Instant createdAt) {
  public TraceabilityEdge {
    if (confidence < 0.0 || confidence > 1.0)
      throw new IllegalArgumentException("confidence must be 0.0–1.0");
  }

  /** Convenience factory for explicit (non-inferred) edges. */
  public static TraceabilityEdge explicit(
      UUID fromId, Tier fromTier, UUID toId, Tier toTier, EdgeType type) {
    return new TraceabilityEdge(
        fromId, fromTier, toId, toTier, type, null, 1.0, Map.of(), Instant.now());
  }
}
