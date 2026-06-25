package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "project_integration_configs",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_pic_project_type",
            columnNames = {"project_id", "integration_type"}))
@EntityListeners(AuditingEntityListener.class)
public class ProjectIntegrationConfig {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "tier", nullable = false, length = 20)
  private String tier;

  @Column(name = "integration_type", nullable = false, length = 40)
  private String integrationType;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "sync_direction", nullable = false, length = 15)
  private String syncDirection = "INBOUND";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "connection_params", nullable = false, columnDefinition = "jsonb")
  private Map<String, String> connectionParams;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "field_mappings", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> fieldMappings;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "filter_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, String> filterConfig;

  @Column(name = "repo_type", nullable = false, length = 20)
  private String repoType = "GENERAL";

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "consecutive_errors", nullable = false)
  private int consecutiveErrors = 0;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProjectIntegrationConfig() {}

  public ProjectIntegrationConfig(
      UUID projectId,
      String tier,
      String integrationType,
      String displayName,
      String syncDirection) {
    this.projectId = projectId;
    this.tier = tier;
    this.integrationType = integrationType;
    this.displayName = displayName;
    this.syncDirection = syncDirection;
  }

  public void recordSyncSuccess() {
    this.lastSyncedAt = Instant.now();
    this.consecutiveErrors = 0;
  }

  public void recordSyncError() {
    this.consecutiveErrors++;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getTier() {
    return tier;
  }

  public String getIntegrationType() {
    return integrationType;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getSyncDirection() {
    return syncDirection;
  }

  public Map<String, String> getConnectionParams() {
    return connectionParams;
  }

  public Map<String, Object> getFieldMappings() {
    return fieldMappings;
  }

  public Map<String, String> getFilterConfig() {
    return filterConfig;
  }

  public String getRepoType() {
    return repoType;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public int getConsecutiveErrors() {
    return consecutiveErrors;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setSyncDirection(String syncDirection) {
    this.syncDirection = syncDirection;
  }

  public void setConnectionParams(Map<String, String> m) {
    this.connectionParams = m;
  }

  public void setFieldMappings(Map<String, Object> m) {
    this.fieldMappings = m;
  }

  public void setFilterConfig(Map<String, String> m) {
    this.filterConfig = m;
  }

  public void setRepoType(String repoType) {
    this.repoType = repoType != null ? repoType : "GENERAL";
  }

  public void setEnabled(boolean v) {
    this.enabled = v;
  }

  public String param(String key) {
    return connectionParams != null ? connectionParams.get(key) : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProjectIntegrationConfig c)) return false;
    return Objects.equals(id, c.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
