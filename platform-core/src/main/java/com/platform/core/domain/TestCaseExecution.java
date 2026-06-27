package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The result of executing one test case within a test run. Status values: PENDING, PASSED, FAILED,
 * BLOCKED, SKIPPED.
 */
@Entity
@Table(name = "test_case_executions")
public class TestCaseExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "test_run_id", nullable = false)
  private UUID testRunId;

  @Column(name = "test_case_id", nullable = false)
  private UUID testCaseId;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "PENDING"; // PENDING, PASSED, FAILED, BLOCKED, SKIPPED

  @Column(name = "actual_result", columnDefinition = "TEXT")
  private String actualResult;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "executed_by", length = 200)
  private String executedBy;

  /** Serialized property combination (e.g. "browser=Chrome;os=Linux"); null if non-parametrized. */
  @Column(name = "property_combo", length = 500)
  private String propertyCombo;

  @Column(name = "executed_at")
  private Instant executedAt;

  // ── Linked ADO work item (defect) — read-only reference; platform never writes to ADO ──
  @Column(name = "defect_external_id", length = 64)
  private String defectExternalId;

  @Column(name = "defect_url", length = 500)
  private String defectUrl;

  @Column(name = "defect_title", length = 500)
  private String defectTitle;

  @Column(name = "defect_state", length = 60)
  private String defectState;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected TestCaseExecution() {}

  public TestCaseExecution(UUID testRunId, UUID testCaseId) {
    this.testRunId = testRunId;
    this.testCaseId = testCaseId;
  }

  public TestCaseExecution(UUID testRunId, UUID testCaseId, String propertyCombo) {
    this(testRunId, testCaseId);
    this.propertyCombo = propertyCombo;
  }

  public void record(String status, String actualResult, String notes, String executedBy) {
    this.status = status;
    this.actualResult = actualResult;
    this.notes = notes;
    this.executedBy = executedBy;
    this.executedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Link an existing ADO work item (validated upstream); never triggers an ADO write. */
  public void linkDefect(String externalId, String url, String title, String state) {
    this.defectExternalId = externalId;
    this.defectUrl = url;
    this.defectTitle = title;
    this.defectState = state;
    this.updatedAt = Instant.now();
  }

  public void clearDefect() {
    this.defectExternalId = null;
    this.defectUrl = null;
    this.defectTitle = null;
    this.defectState = null;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTestRunId() {
    return testRunId;
  }

  public UUID getTestCaseId() {
    return testCaseId;
  }

  public String getStatus() {
    return status;
  }

  public String getActualResult() {
    return actualResult;
  }

  public String getNotes() {
    return notes;
  }

  public String getExecutedBy() {
    return executedBy;
  }

  public String getPropertyCombo() {
    return propertyCombo;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public String getDefectExternalId() {
    return defectExternalId;
  }

  public String getDefectUrl() {
    return defectUrl;
  }

  public String getDefectTitle() {
    return defectTitle;
  }

  public String getDefectState() {
    return defectState;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TestCaseExecution e)) return false;
    return Objects.equals(id, e.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
