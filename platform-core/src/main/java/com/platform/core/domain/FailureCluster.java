package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A group of recurring test failures that share a normalized failure signature. Layers on top of
 * the external tracker (ADO/Jira/…): the platform owns the cluster, the tracker owns the defect.
 * Marking a cluster KNOWN_ISSUE suppresses re-alerting; a resolved cluster reopens when a new
 * occurrence lands.
 */
@Entity
@Table(
    name = "failure_cluster",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "signature_hash"}),
    indexes = @Index(name = "idx_failure_cluster_project_status", columnList = "project_id,status"))
public class FailureCluster {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "source", nullable = false, length = 20)
  private String source; // AUTOMATED | MANUAL

  @Column(name = "signature_hash", nullable = false, length = 64)
  private String signatureHash;

  @Column(name = "test_ref", length = 500)
  private String testRef;

  @Column(name = "title", length = 500)
  private String title;

  @Column(name = "sample_message", columnDefinition = "TEXT")
  private String sampleMessage;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "OPEN"; // OPEN | KNOWN_ISSUE | RESOLVED

  @Column(name = "defect_external_id", length = 64)
  private String defectExternalId;

  @Column(name = "defect_url", length = 500)
  private String defectUrl;

  @Column(name = "defect_title", length = 500)
  private String defectTitle;

  @Column(name = "defect_state", length = 60)
  private String defectState;

  @Column(name = "occurrences", nullable = false)
  private int occurrences = 0;

  /**
   * The in-flight targeted retest run for this cluster, or null. Cleared once the run is evaluated.
   */
  @Column(name = "retest_run_id")
  private UUID retestRunId;

  @Column(name = "first_seen_at", nullable = false, updatable = false)
  private Instant firstSeenAt = Instant.now();

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt = Instant.now();

  protected FailureCluster() {}

  public FailureCluster(
      UUID projectId,
      String source,
      String signatureHash,
      String testRef,
      String title,
      String sampleMessage) {
    this.projectId = projectId;
    this.source = source;
    this.signatureHash = signatureHash;
    this.testRef = testRef;
    this.title = title;
    this.sampleMessage = sampleMessage;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getSource() {
    return source;
  }

  public String getSignatureHash() {
    return signatureHash;
  }

  public String getTestRef() {
    return testRef;
  }

  public String getTitle() {
    return title;
  }

  public String getSampleMessage() {
    return sampleMessage;
  }

  public String getStatus() {
    return status;
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

  public int getOccurrences() {
    return occurrences;
  }

  public UUID getRetestRunId() {
    return retestRunId;
  }

  public Instant getFirstSeenAt() {
    return firstSeenAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setOccurrences(int occurrences) {
    this.occurrences = occurrences;
  }

  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setSampleMessage(String sampleMessage) {
    this.sampleMessage = sampleMessage;
  }

  public void setRetestRunId(UUID retestRunId) {
    this.retestRunId = retestRunId;
  }

  public void setDefect(String externalId, String url, String title, String state) {
    this.defectExternalId = externalId;
    this.defectUrl = url;
    this.defectTitle = title;
    this.defectState = state;
  }
}
