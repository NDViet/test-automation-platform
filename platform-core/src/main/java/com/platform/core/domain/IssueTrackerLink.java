package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps a test ID ↔ issue tracker ticket (JIRA key, Linear ID, GitHub issue number).
 * Unique per (testId, projectId, trackerType) so each test has at most one open
 * ticket per tracker.
 */
@Entity
@Table(name = "issue_tracker_links",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_link_test_project_tracker",
               columnNames = {"test_id", "project_id", "tracker_type"}))
@EntityListeners(AuditingEntityListener.class)
public class IssueTrackerLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "test_id", nullable = false, length = 500)
    private String testId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "tracker_type", nullable = false, length = 20)
    private String trackerType;

    @Column(name = "issue_key", nullable = false, length = 50)
    private String issueKey;

    @Column(name = "issue_url", length = 1000)
    private String issueUrl;

    @Column(name = "issue_status", length = 50)
    private String issueStatus;

    @Column(name = "issue_type", length = 50)
    private String issueType;

    @CreatedDate
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected IssueTrackerLink() {}

    public IssueTrackerLink(String testId, UUID projectId, String trackerType,
                             String issueKey, String issueUrl, String issueType) {
        this.testId      = testId;
        this.projectId   = projectId;
        this.trackerType = trackerType;
        this.issueKey    = issueKey;
        this.issueUrl    = issueUrl;
        this.issueType   = issueType;
        this.issueStatus = "Open";
    }

    public UUID getId()           { return id; }
    public String getTestId()     { return testId; }
    public UUID getProjectId()    { return projectId; }
    public String getTrackerType(){ return trackerType; }
    public String getIssueKey()   { return issueKey; }
    public String getIssueUrl()   { return issueUrl; }
    public String getIssueStatus(){ return issueStatus; }
    public String getIssueType()  { return issueType; }
    public Instant getLinkedAt()  { return linkedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }

    public void syncStatus(String newStatus) {
        this.issueStatus    = newStatus;
        this.lastSyncedAt   = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IssueTrackerLink l)) return false;
        return Objects.equals(id, l.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
