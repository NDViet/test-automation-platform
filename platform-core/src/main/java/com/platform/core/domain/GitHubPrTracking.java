package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per open PR per project. Records the last head SHA that triggered an analysis workflow. A
 * new workflow is only triggered when head_sha changes — filtering out comment/label/CI-rerun noise
 * that bumps GitHub's updated_at without changing code.
 */
@Entity
@Table(
    name = "github_pr_tracking",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_github_pr_tracking",
            columnNames = {"project_id", "repo_full_name", "pr_number"}))
public class GitHubPrTracking {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "repo_full_name", nullable = false, length = 200)
  private String repoFullName;

  @Column(name = "pr_number", nullable = false)
  private int prNumber;

  @Column(name = "head_sha", nullable = false, length = 40)
  private String headSha;

  @Column(name = "pr_url", length = 500)
  private String prUrl;

  @Column(name = "last_triggered_at", nullable = false)
  private Instant lastTriggeredAt;

  @Column(name = "triggered_workflow_id")
  private UUID triggeredWorkflowId;

  protected GitHubPrTracking() {}

  public GitHubPrTracking(
      UUID projectId,
      String repoFullName,
      int prNumber,
      String headSha,
      String prUrl,
      UUID workflowId) {
    this.projectId = projectId;
    this.repoFullName = repoFullName;
    this.prNumber = prNumber;
    this.headSha = headSha;
    this.prUrl = prUrl;
    this.lastTriggeredAt = Instant.now();
    this.triggeredWorkflowId = workflowId;
  }

  public void update(String newHeadSha, UUID newWorkflowId) {
    this.headSha = newHeadSha;
    this.lastTriggeredAt = Instant.now();
    this.triggeredWorkflowId = newWorkflowId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getRepoFullName() {
    return repoFullName;
  }

  public int getPrNumber() {
    return prNumber;
  }

  public String getHeadSha() {
    return headSha;
  }

  public String getPrUrl() {
    return prUrl;
  }

  public Instant getLastTriggeredAt() {
    return lastTriggeredAt;
  }

  public UUID getTriggeredWorkflowId() {
    return triggeredWorkflowId;
  }
}
