package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable snapshot of a test case (title/status/steps/tags as JSON) captured on each save, for
 * version history + diff.
 */
@Entity
@Table(name = "test_case_revision")
public class TestCaseRevision {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "test_case_id", nullable = false)
  private UUID testCaseId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "snapshot", nullable = false, columnDefinition = "TEXT")
  private String snapshot;

  @Column(name = "changed_by", length = 100)
  private String changedBy;

  @Column(name = "change_kind", length = 30)
  private String changeKind;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt = Instant.now();

  protected TestCaseRevision() {}

  public TestCaseRevision(
      UUID testCaseId,
      UUID projectId,
      int version,
      String snapshot,
      String changedBy,
      String changeKind) {
    this.testCaseId = testCaseId;
    this.projectId = projectId;
    this.version = version;
    this.snapshot = snapshot;
    this.changedBy = changedBy;
    this.changeKind = changeKind;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTestCaseId() {
    return testCaseId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public int getVersion() {
    return version;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public String getChangedBy() {
    return changedBy;
  }

  public String getChangeKind() {
    return changeKind;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
