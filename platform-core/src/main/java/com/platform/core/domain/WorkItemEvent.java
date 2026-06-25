package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A single field change in a work item's ADO history (state transition or assignment). */
@Entity
@Table(name = "work_item_events")
public class WorkItemEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "external_id", nullable = false)
  private String externalId;

  @Column(name = "issue_type")
  private String issueType;

  @Column(nullable = false)
  private int rev;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column private String field;

  @Column(name = "from_value", columnDefinition = "TEXT")
  private String fromValue;

  @Column(name = "to_value", columnDefinition = "TEXT")
  private String toValue;

  @Column(name = "from_category")
  private String fromCategory;

  @Column(name = "to_category")
  private String toCategory;

  @Column(name = "actor_name")
  private String actorName;

  @Column(name = "actor_unique")
  private String actorUnique;

  @Column(name = "revised_at")
  private Instant revisedAt;

  protected WorkItemEvent() {}

  public WorkItemEvent(
      UUID projectId,
      String externalId,
      String issueType,
      int rev,
      String eventType,
      String field,
      String fromValue,
      String toValue,
      String fromCategory,
      String toCategory,
      String actorName,
      String actorUnique,
      Instant revisedAt) {
    this.projectId = projectId;
    this.externalId = externalId;
    this.issueType = issueType;
    this.rev = rev;
    this.eventType = eventType;
    this.field = field;
    this.fromValue = fromValue;
    this.toValue = toValue;
    this.fromCategory = fromCategory;
    this.toCategory = toCategory;
    this.actorName = actorName;
    this.actorUnique = actorUnique;
    this.revisedAt = revisedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getExternalId() {
    return externalId;
  }

  public int getRev() {
    return rev;
  }

  public String getEventType() {
    return eventType;
  }

  public String getField() {
    return field;
  }
}
