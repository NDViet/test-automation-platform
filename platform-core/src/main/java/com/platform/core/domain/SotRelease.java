package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sot_releases")
public class SotRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    /** VERSION | SPRINT | MILESTONE */
    @Column(name = "release_type", nullable = false, length = 20)
    private String releaseType = "VERSION";

    @Column(name = "external_id", columnDefinition = "TEXT")
    private String externalId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** PLANNED | IN_PROGRESS | RELEASED | ARCHIVED */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "PLANNED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SotRelease() {}

    public SotRelease(UUID projectId, String name, String releaseType, String externalId) {
        this.projectId   = projectId;
        this.name        = name;
        this.releaseType = releaseType != null ? releaseType : "VERSION";
        this.externalId  = externalId;
    }

    public void activate() { this.state = "IN_PROGRESS"; this.updatedAt = Instant.now(); }
    public void release()  { this.state = "RELEASED";    this.updatedAt = Instant.now(); }
    public void archive()  { this.state = "ARCHIVED";    this.updatedAt = Instant.now(); }

    public UUID getId()          { return id; }
    public UUID getProjectId()   { return projectId; }
    public String getName()      { return name; }
    public String getReleaseType() { return releaseType; }
    public String getExternalId(){ return externalId; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getState()     { return state; }
    public Instant getCreatedAt(){ return createdAt; }
    public Instant getUpdatedAt(){ return updatedAt; }

    public void setTargetDate(LocalDate d) { this.targetDate = d; this.updatedAt = Instant.now(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SotRelease r)) return false;
        return Objects.equals(id, r.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
