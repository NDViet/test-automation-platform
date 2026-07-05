package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A schedule that dispatches a GitHub Actions {@code workflow_dispatch} on an interval. The poller
 * selects enabled schedules whose {@link #nextRunAt} is due, dispatches, then advances {@code
 * nextRunAt} by {@link #intervalMinutes}. {@code inputs} holds the workflow inputs as a JSON
 * string.
 */
@Entity
@Table(name = "run_schedules")
public class RunSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "repo_full_name", nullable = false, length = 300)
  private String repoFullName;

  @Column(name = "workflow_id", nullable = false)
  private long workflowId;

  @Column(name = "ref", nullable = false, length = 200)
  private String ref = "main";

  @Column(name = "inputs", columnDefinition = "TEXT")
  private String inputs;

  @Column(name = "interval_minutes", nullable = false)
  private int intervalMinutes;

  @Column(name = "next_run_at")
  private Instant nextRunAt;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "last_dispatched_at")
  private Instant lastDispatchedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected RunSchedule() {}

  public RunSchedule(
      UUID projectId,
      String name,
      String repoFullName,
      long workflowId,
      String ref,
      String inputs,
      int intervalMinutes,
      boolean enabled,
      Instant nextRunAt) {
    this.projectId = projectId;
    this.name = name;
    this.repoFullName = repoFullName;
    this.workflowId = workflowId;
    this.ref = (ref != null && !ref.isBlank()) ? ref : "main";
    this.inputs = inputs;
    this.intervalMinutes = intervalMinutes;
    this.enabled = enabled;
    this.nextRunAt = nextRunAt;
  }

  public void update(
      String name,
      String repoFullName,
      long workflowId,
      String ref,
      String inputs,
      int intervalMinutes,
      boolean enabled,
      Instant nextRunAt) {
    this.name = name;
    this.repoFullName = repoFullName;
    this.workflowId = workflowId;
    this.ref = (ref != null && !ref.isBlank()) ? ref : "main";
    this.inputs = inputs;
    this.intervalMinutes = intervalMinutes;
    this.enabled = enabled;
    this.nextRunAt = nextRunAt;
    this.updatedAt = Instant.now();
  }

  /** Records a dispatch at {@code when} and advances the next run by the interval. */
  public void markDispatched(Instant when) {
    this.lastDispatchedAt = when;
    this.nextRunAt = when.plus(intervalMinutes, ChronoUnit.MINUTES);
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getRepoFullName() {
    return repoFullName;
  }

  public long getWorkflowId() {
    return workflowId;
  }

  public String getRef() {
    return ref;
  }

  public String getInputs() {
    return inputs;
  }

  public int getIntervalMinutes() {
    return intervalMinutes;
  }

  public Instant getNextRunAt() {
    return nextRunAt;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getLastDispatchedAt() {
    return lastDispatchedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
