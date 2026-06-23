package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Logical grouping of test cases within a project. */
@Entity
@Table(name = "test_suites")
public class TestSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Parent suite for hierarchical plans (Kiwi-style tree); null for a root suite. */
    @Column(name = "parent_id")
    private UUID parentId;

    /** SMOKE, REGRESSION, SANITY, FUNCTIONAL, FEATURE, DOMAIN, ... (free-form, optional). */
    @Column(name = "plan_type", length = 40)
    private String planType;

    // ── Ownership scope (optional): which Area/Team this suite belongs to. ──
    @Column(name = "area_path", length = 1000)
    private String areaPath;

    @Column(name = "team_id")
    private UUID teamId;

    /** STATIC = curated members; SMART = resolved from the filter below. */
    @Column(name = "selection_mode", nullable = false, length = 10)
    private String selectionMode = "STATIC";

    // ── SMART filter (used when selection_mode = SMART). Area/Team come from the
    //    ownership scope above; these add iteration / status / tags. ──
    @Column(name = "filter_iteration", length = 1000)
    private String filterIteration;

    @Column(name = "filter_status", length = 20)
    private String filterStatus;

    /** Comma-separated tags; a case matches if it carries ANY of them. */
    @Column(name = "filter_tags", columnDefinition = "TEXT")
    private String filterTags;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TestSuite() {}

    public TestSuite(UUID projectId, String name, String description) {
        this.projectId   = projectId;
        this.name        = name;
        this.description = description;
    }

    public TestSuite(UUID projectId, String name, String description,
                     UUID parentId, String planType) {
        this(projectId, name, description);
        this.parentId = parentId;
        this.planType = planType;
    }

    public UUID getId()          { return id; }
    public UUID getProjectId()   { return projectId; }
    public String getName()      { return name; }
    public String getDescription() { return description; }
    public UUID getParentId()    { return parentId; }
    public String getPlanType()  { return planType; }
    public String getAreaPath()       { return areaPath; }
    public UUID getTeamId()           { return teamId; }
    public String getSelectionMode()  { return selectionMode; }
    public String getFilterIteration(){ return filterIteration; }
    public String getFilterStatus()   { return filterStatus; }
    public String getFilterTags()     { return filterTags; }
    public boolean isSmart()          { return "SMART".equalsIgnoreCase(selectionMode); }
    public boolean isActive()    { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) {
        this.name      = name;
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt   = Instant.now();
    }

    public void setParentId(UUID parentId) {
        this.parentId  = parentId;
        this.updatedAt = Instant.now();
    }

    public void setPlanType(String planType) {
        this.planType  = planType;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        this.active    = active;
        this.updatedAt = Instant.now();
    }

    /** Sets ownership scope (area/team) and the smart selection config. */
    public void setScopeAndMode(String areaPath, UUID teamId, String selectionMode,
                                String filterIteration, String filterStatus, String filterTags) {
        this.areaPath        = blankToNull(areaPath);
        this.teamId          = teamId;
        this.selectionMode   = "SMART".equalsIgnoreCase(selectionMode) ? "SMART" : "STATIC";
        this.filterIteration = blankToNull(filterIteration);
        this.filterStatus    = blankToNull(filterStatus);
        this.filterTags      = blankToNull(filterTags);
        this.updatedAt       = Instant.now();
    }

    private static String blankToNull(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestSuite s)) return false;
        return Objects.equals(id, s.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
