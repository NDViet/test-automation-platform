package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A named, property-bearing execution context (e.g. STAGING, PROD-EU) a run targets. */
@Entity
@Table(name = "environments")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Environment() {}

    public Environment(UUID projectId, String name, String description) {
        this.projectId   = projectId;
        this.name        = name;
        this.description = description;
    }

    public UUID getId()          { return id; }
    public UUID getProjectId()   { return projectId; }
    public String getName()      { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name)               { this.name = name; }
    public void setDescription(String description)  { this.description = description; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Environment e)) return false;
        return Objects.equals(id, e.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
