package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A per-project notification route: which events go to which channel/destination. */
@Entity
@Table(name = "notification_pref")
public class NotificationPref {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "channel", nullable = false, length = 20)
  private String channel; // SLACK | EMAIL | WEBHOOK

  @Column(name = "destination", nullable = false, length = 500)
  private String destination;

  @Column(name = "events", nullable = false, columnDefinition = "TEXT")
  private String events; // JSON array of event names

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected NotificationPref() {}

  public NotificationPref(
      UUID projectId, String channel, String destination, String events, boolean enabled) {
    this.projectId = projectId;
    this.channel = channel;
    this.destination = destination;
    this.events = events;
    this.enabled = enabled;
  }

  public void update(String channel, String destination, String events, boolean enabled) {
    this.channel = channel;
    this.destination = destination;
    this.events = events;
    this.enabled = enabled;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getChannel() {
    return channel;
  }

  public String getDestination() {
    return destination;
  }

  public String getEvents() {
    return events;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
