package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Canonical requirement stored by the platform, synced from JIRA, Linear, GitHub Issues, etc. The
 * source is tracked via integration_config_id; external_id is the ticket key in that system.
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

  // Added by V59 — full upstream payload for provenance / re-mapping / drift safety
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_upstream", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rawUpstream = Map.of();

  // Added by V60 — denormalized ADO dimensions for dashboard grouping/filtering
  @Column(name = "area_path", length = 1000)
  private String areaPath;

  @Column(name = "iteration_path", length = 1000)
  private String iterationPath;

  @Column(name = "assigned_to", length = 400)
  private String assignedTo;

  // Added by V62 — last ADO revision whose history was extracted into work_item_events
  @Column(name = "history_rev")
  private Integer historyRev;

  // Added by V63 — upstream creation date (ADO System.CreatedDate), for sort + display
  @Column(name = "created_date")
  private Instant createdDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected PlatformRequirement() {}

  public PlatformRequirement(
      UUID projectId,
      UUID integrationConfigId,
      String externalId,
      String title,
      String description,
      String issueType) {
    this.projectId = projectId;
    this.integrationConfigId = integrationConfigId;
    this.externalId = externalId;
    this.title = title;
    this.description = description;
    this.issueType = issueType != null ? issueType : "STORY";
    this.acceptanceCriteria = List.of();
    this.syncedAt = Instant.now();
  }

  public void updateFromSync(String title, String description) {
    this.title = title;
    this.description = description;
    this.syncedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Merge-safe, non-destructive sync update: a field is overwritten only when the incoming value is
   * present (non-blank). A field that disappeared upstream (null/blank) keeps its last-known value
   * rather than being clobbered — the integrity guarantee for schema drift. The full {@code
   * rawUpstream} snapshot is always stored when provided.
   *
   * @return true if any canonical field actually changed
   */
  public boolean mergeFromSync(
      String title,
      String description,
      String issueType,
      String status,
      String priority,
      Map<String, Object> rawUpstream) {
    boolean changed = false;
    if (isPresent(title) && !title.equals(this.title)) {
      this.title = title;
      changed = true;
    }
    if (isPresent(description) && !description.equals(this.description)) {
      this.description = description;
      changed = true;
    }
    if (isPresent(issueType) && !issueType.equals(this.issueType)) {
      this.issueType = issueType;
      changed = true;
    }
    if (isPresent(status) && !status.equals(this.status)) {
      this.status = status;
      changed = true;
    }
    if (isPresent(priority) && !priority.equals(this.priority)) {
      this.priority = priority;
      changed = true;
    }
    if (rawUpstream != null && !rawUpstream.isEmpty()) {
      this.rawUpstream = rawUpstream;
    }
    this.syncedAt = Instant.now();
    this.updatedAt = Instant.now();
    return changed;
  }

  private static boolean isPresent(String v) {
    return v != null && !v.isBlank();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getIntegrationConfigId() {
    return integrationConfigId;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public List<Object> getAcceptanceCriteria() {
    return acceptanceCriteria;
  }

  public String getIssueType() {
    return issueType;
  }

  public String getStatus() {
    return status;
  }

  public String getPriority() {
    return priority;
  }

  public UUID getParentId() {
    return parentId;
  }

  public int getDepth() {
    return depth;
  }

  public String getVersionHash() {
    return versionHash;
  }

  public String getPrevVersionHash() {
    return prevVersionHash;
  }

  public String getChangeSummary() {
    return changeSummary;
  }

  public Map<String, Object> getRawUpstream() {
    return rawUpstream;
  }

  public String getAreaPath() {
    return areaPath;
  }

  public String getIterationPath() {
    return iterationPath;
  }

  public String getAssignedTo() {
    return assignedTo;
  }

  public Integer getHistoryRev() {
    return historyRev;
  }

  public void setHistoryRev(Integer rev) {
    this.historyRev = rev;
  }

  public Instant getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(Instant v) {
    if (v != null) this.createdDate = v;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  /** Denormalized ADO dimensions; null/blank values are ignored (keep last-known). */
  public void setDimensions(String areaPath, String iterationPath, String assignedTo) {
    if (areaPath != null && !areaPath.isBlank()) this.areaPath = areaPath;
    if (iterationPath != null && !iterationPath.isBlank()) this.iterationPath = iterationPath;
    if (assignedTo != null && !assignedTo.isBlank()) this.assignedTo = assignedTo;
  }

  public void setAcceptanceCriteria(List<Object> acceptanceCriteria) {
    this.acceptanceCriteria = acceptanceCriteria != null ? acceptanceCriteria : List.of();
    this.updatedAt = Instant.now();
  }

  /** Sets parent + tree depth from a pulled hierarchy relation. */
  public void setHierarchy(UUID parentId, int depth) {
    this.parentId = parentId;
    this.depth = depth;
    this.updatedAt = Instant.now();
  }

  public void setIssueType(String issueType) {
    this.issueType = issueType;
  }

  public void setRawUpstream(Map<String, Object> rawUpstream) {
    this.rawUpstream = rawUpstream != null ? rawUpstream : Map.of();
  }

  public void setVersionHash(String current, String prev, String summary) {
    this.prevVersionHash = prev;
    this.versionHash = current;
    this.changeSummary = summary;
    this.updatedAt = Instant.now();
  }

  public Instant getSyncedAt() {
    return syncedAt;
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
    if (!(o instanceof PlatformRequirement r)) return false;
    return Objects.equals(id, r.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
