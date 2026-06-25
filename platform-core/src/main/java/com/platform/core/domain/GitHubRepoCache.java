package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Cache of all GitHub repos accessible to a PAT credential. Refreshed on demand via "Sync". */
@Entity
@Table(
    name = "github_repo_cache",
    indexes = @Index(name = "idx_ghrc_credential_id", columnList = "credential_id"))
public class GitHubRepoCache {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "credential_id", nullable = false)
  private UUID credentialId;

  @Column(name = "full_name", nullable = false, length = 400)
  private String fullName;

  @Column(name = "owner", length = 200)
  private String owner;

  @Column(name = "repo_name", length = 200)
  private String repoName;

  @Column(name = "is_private", nullable = false)
  private boolean isPrivate;

  @Column(name = "default_branch", length = 200)
  private String defaultBranch;

  @Column(name = "html_url", length = 500)
  private String htmlUrl;

  @Column(name = "synced_at", nullable = false)
  private Instant syncedAt;

  protected GitHubRepoCache() {}

  public GitHubRepoCache(
      UUID credentialId,
      String fullName,
      String owner,
      String repoName,
      boolean isPrivate,
      String defaultBranch,
      String htmlUrl,
      Instant syncedAt) {
    this.credentialId = credentialId;
    this.fullName = fullName;
    this.owner = owner;
    this.repoName = repoName;
    this.isPrivate = isPrivate;
    this.defaultBranch = defaultBranch;
    this.htmlUrl = htmlUrl;
    this.syncedAt = syncedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCredentialId() {
    return credentialId;
  }

  public String getFullName() {
    return fullName;
  }

  public String getOwner() {
    return owner;
  }

  public String getRepoName() {
    return repoName;
  }

  public boolean isPrivate() {
    return isPrivate;
  }

  public String getDefaultBranch() {
    return defaultBranch;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public Instant getSyncedAt() {
    return syncedAt;
  }
}
