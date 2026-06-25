package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "impact_analyses")
@EntityListeners(AuditingEntityListener.class)
public class ImpactAnalysis {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "name", nullable = false)
  private String name = "Impact Analysis";

  @Column(name = "status", nullable = false, length = 50)
  private String status = "DRAFT";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "linked_prs", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> linkedPrs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "linked_requirement_ids", nullable = false, columnDefinition = "jsonb")
  private List<String> linkedRequirementIds;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "suggestions", columnDefinition = "jsonb")
  private Map<String, Object> suggestions;

  @Column(name = "summary", columnDefinition = "text")
  private String summary;

  @Column(name = "workflow_id")
  private UUID workflowId;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ImpactAnalysis() {}

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public List<Map<String, Object>> getLinkedPrs() {
    return linkedPrs;
  }

  public List<String> getLinkedRequirementIds() {
    return linkedRequirementIds;
  }

  public Map<String, Object> getSuggestions() {
    return suggestions;
  }

  public String getSummary() {
    return summary;
  }

  public UUID getWorkflowId() {
    return workflowId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setProjectId(UUID projectId) {
    this.projectId = projectId;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setLinkedPrs(List<Map<String, Object>> linkedPrs) {
    this.linkedPrs = linkedPrs;
  }

  public void setLinkedRequirementIds(List<String> linkedRequirementIds) {
    this.linkedRequirementIds = linkedRequirementIds;
  }

  public void setSuggestions(Map<String, Object> suggestions) {
    this.suggestions = suggestions;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public void setWorkflowId(UUID workflowId) {
    this.workflowId = workflowId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ImpactAnalysis a)) return false;
    return Objects.equals(id, a.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
