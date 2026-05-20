package com.platform.core.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "platform_traceability_edges")
public class PlatformTraceabilityEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "from_id", nullable = false)
    private UUID fromId;

    @Column(name = "from_tier", nullable = false, length = 20)
    private String fromTier;

    @Column(name = "to_id", nullable = false)
    private UUID toId;

    @Column(name = "to_tier", nullable = false, length = 20)
    private String toTier;

    /** COVERED_BY | AUTOMATED_BY | RAN_IN | MONITORED_BY | FOUND_BY | PARENT_OF | LINKED_TO */
    @Column(name = "edge_type", nullable = false, length = 30)
    private String edgeType;

    @Column(name = "link_subtype", length = 30)
    private String linkSubtype;

    @Column(name = "confidence", nullable = false)
    private double confidence = 1.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PlatformTraceabilityEdge() {}

    public PlatformTraceabilityEdge(UUID projectId,
                                     UUID fromId, String fromTier,
                                     UUID toId,   String toTier,
                                     String edgeType) {
        this.projectId = projectId;
        this.fromId    = fromId;
        this.fromTier  = fromTier;
        this.toId      = toId;
        this.toTier    = toTier;
        this.edgeType  = edgeType;
    }

    public PlatformTraceabilityEdge withSubtype(String subtype) {
        this.linkSubtype = subtype;
        return this;
    }

    public PlatformTraceabilityEdge withConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public PlatformTraceabilityEdge withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : Map.of();
        return this;
    }

    public UUID getId()          { return id; }
    public UUID getProjectId()   { return projectId; }
    public UUID getFromId()      { return fromId; }
    public String getFromTier()  { return fromTier; }
    public UUID getToId()        { return toId; }
    public String getToTier()    { return toTier; }
    public String getEdgeType()  { return edgeType; }
    public String getLinkSubtype() { return linkSubtype; }
    public double getConfidence(){ return confidence; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getCreatedAt(){ return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlatformTraceabilityEdge e)) return false;
        return Objects.equals(id, e.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
