package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A manual test execution run scoped to a project, optionally tied to a release version. */
@Entity
@Table(name = "test_runs")
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "release_version", length = 100)
    private String releaseVersion;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment = "STAGING";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "IN_PROGRESS"; // IN_PROGRESS, COMPLETED, ABANDONED

    @Column(name = "triggered_by", length = 200)
    private String triggeredBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TestRun() {}

    public TestRun(UUID projectId, String name, String releaseVersion,
                   String environment, String triggeredBy) {
        this.projectId      = projectId;
        this.name           = name;
        this.releaseVersion = releaseVersion;
        this.environment    = environment != null ? environment : "STAGING";
        this.triggeredBy    = triggeredBy;
    }

    public void complete() {
        this.status      = "COMPLETED";
        this.completedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void abandon() {
        this.status      = "ABANDONED";
        this.completedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public UUID getId()              { return id; }
    public UUID getProjectId()       { return projectId; }
    public String getName()          { return name; }
    public String getReleaseVersion() { return releaseVersion; }
    public String getEnvironment()   { return environment; }
    public String getStatus()        { return status; }
    public String getTriggeredBy()   { return triggeredBy; }
    public Instant getStartedAt()    { return startedAt; }
    public Instant getCompletedAt()  { return completedAt; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }

    public void setName(String name) {
        this.name      = name;
        this.updatedAt = Instant.now();
    }

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
        this.updatedAt      = Instant.now();
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
        this.updatedAt = Instant.now();
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
        this.updatedAt   = Instant.now();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestRun r)) return false;
        return Objects.equals(id, r.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
