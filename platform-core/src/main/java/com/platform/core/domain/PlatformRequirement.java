package com.platform.core.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical requirement stored by the platform, synced from JIRA, Linear, GitHub Issues, etc.
 * The source is tracked via integration_config_id; external_id is the ticket key in that system.
 */
@Entity
@Table(name = "platform_requirements")
public class PlatformRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "integration_config_id")
    private UUID integrationConfigId;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acceptance_criteria", nullable = false, columnDefinition = "jsonb")
    private List<Object> acceptanceCriteria;

    @Column(name = "issue_type", nullable = false, length = 20)
    private String issueType = "STORY";

    @Column(name = "status", nullable = false, length = 50)
    private String status = "OPEN";

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "depth", nullable = false)
    private int depth = 0;

    @Column(name = "synced_at")
    private Instant syncedAt;

    // Added by V33 — version tracking for change detection
    @Column(name = "version_hash", length = 64)
    private String versionHash;

    @Column(name = "prev_version_hash", length = 64)
    private String prevVersionHash;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlatformRequirement() {}

    public PlatformRequirement(UUID projectId, UUID integrationConfigId, String externalId,
                                String title, String description, String issueType) {
        this.projectId           = projectId;
        this.integrationConfigId = integrationConfigId;
        this.externalId          = externalId;
        this.title               = title;
        this.description         = description;
        this.issueType           = issueType != null ? issueType : "STORY";
        this.acceptanceCriteria  = List.of();
        this.syncedAt            = Instant.now();
    }

    public void updateFromSync(String title, String description) {
        this.title       = title;
        this.description = description;
        this.syncedAt    = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public UUID getId()                      { return id; }
    public UUID getProjectId()               { return projectId; }
    public UUID getIntegrationConfigId()     { return integrationConfigId; }
    public String getExternalId()            { return externalId; }
    public String getTitle()                 { return title; }
    public String getDescription()           { return description; }
    public List<Object> getAcceptanceCriteria() { return acceptanceCriteria; }
    public String getIssueType()             { return issueType; }
    public String getStatus()                { return status; }
    public String getPriority()              { return priority; }
    public UUID getParentId()                { return parentId; }
    public int getDepth()                    { return depth; }
    public String getVersionHash()           { return versionHash; }
    public String getPrevVersionHash()       { return prevVersionHash; }
    public String getChangeSummary()         { return changeSummary; }

    public void setVersionHash(String current, String prev, String summary) {
        this.prevVersionHash = prev;
        this.versionHash     = current;
        this.changeSummary   = summary;
        this.updatedAt       = Instant.now();
    }
    public Instant getSyncedAt()             { return syncedAt; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlatformRequirement r)) return false;
        return Objects.equals(id, r.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
