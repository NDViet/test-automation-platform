package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sot_release_requirements")
@IdClass(SotReleaseRequirement.PK.class)
public class SotReleaseRequirement {

    @Id
    @Column(name = "release_id", nullable = false)
    private java.util.UUID releaseId;

    @Id
    @Column(name = "requirement_id", nullable = false)
    private java.util.UUID requirementId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    protected SotReleaseRequirement() {}

    public SotReleaseRequirement(java.util.UUID releaseId, java.util.UUID requirementId) {
        this.releaseId     = releaseId;
        this.requirementId = requirementId;
    }

    public java.util.UUID getReleaseId()     { return releaseId; }
    public java.util.UUID getRequirementId() { return requirementId; }
    public Instant getAddedAt()              { return addedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SotReleaseRequirement r)) return false;
        return Objects.equals(releaseId, r.releaseId) &&
               Objects.equals(requirementId, r.requirementId);
    }
    @Override public int hashCode() { return Objects.hash(releaseId, requirementId); }

    // Composite PK class
    public record PK(java.util.UUID releaseId, java.util.UUID requirementId)
            implements java.io.Serializable {}
}
