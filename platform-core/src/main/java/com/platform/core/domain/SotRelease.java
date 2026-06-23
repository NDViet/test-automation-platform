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

    // ── Optional COMPOSITE mapping to upstream (ADO) concepts ───────────────────
    // A release lives in an Iteration but is narrowed by Area/Team. Each set dimension
    // is AND-combined; all null = standalone platform release. Auto-associated scope =
    // requirements matching every dimension that is set:
    //   map_iteration_path → iteration_path = value
    //   map_area_path      → area_path = value
    //   map_team_id        → area_path within the team's owned subtree
    //   map_tag            → tag present in labels
    //   mapping_field/value → raw_upstream[field] = value (advanced)
    @Column(name = "map_iteration_path", length = 1000)
    private String mapIterationPath;

    @Column(name = "map_area_path", length = 1000)
    private String mapAreaPath;

    @Column(name = "map_team_id")
    private UUID mapTeamId;

    @Column(name = "map_tag", length = 200)
    private String mapTag;

    @Column(name = "mapping_field", length = 200)
    private String mappingField;

    @Column(name = "mapping_value", length = 1000)
    private String mappingValue;

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
    public String getMapIterationPath() { return mapIterationPath; }
    public String getMapAreaPath()      { return mapAreaPath; }
    public UUID getMapTeamId()          { return mapTeamId; }
    public String getMapTag()           { return mapTag; }
    public String getMappingField() { return mappingField; }
    public String getMappingValue() { return mappingValue; }
    /** True when at least one mapping dimension is set (i.e. not a standalone release). */
    public boolean isMapped() {
        return mapIterationPath != null || mapAreaPath != null || mapTeamId != null
                || mapTag != null || (mappingField != null && mappingValue != null);
    }
    public Instant getCreatedAt(){ return createdAt; }
    public Instant getUpdatedAt(){ return updatedAt; }

    public void setName(String name)       { this.name = name; this.updatedAt = Instant.now(); }
    public void setReleaseType(String t)   { this.releaseType = t != null ? t : "VERSION"; this.updatedAt = Instant.now(); }
    public void setState(String state)     { this.state = state; this.updatedAt = Instant.now(); }
    public void setExternalId(String e)    { this.externalId = e; this.updatedAt = Instant.now(); }
    public void setTargetDate(LocalDate d) { this.targetDate = d; this.updatedAt = Instant.now(); }

    /** Sets the composite upstream mapping; blanks → null (dimension not constrained). */
    public void setMapping(String iterationPath, String areaPath, UUID teamId,
                           String tag, String field, String fieldValue) {
        this.mapIterationPath = blankToNull(iterationPath);
        this.mapAreaPath      = blankToNull(areaPath);
        this.mapTeamId        = teamId;
        this.mapTag           = blankToNull(tag);
        this.mappingField     = blankToNull(field);
        this.mappingValue     = blankToNull(fieldValue);
        this.updatedAt        = Instant.now();
    }

    private static String blankToNull(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SotRelease r)) return false;
        return Objects.equals(id, r.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
