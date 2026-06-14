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

    /** SMOKE, REGRESSION, SANITY, FUNCTIONAL, INTEGRATION, ... (free-form, optional). */
    @Column(name = "plan_type", length = 40)
    private String planType;

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

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestSuite s)) return false;
        return Objects.equals(id, s.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
