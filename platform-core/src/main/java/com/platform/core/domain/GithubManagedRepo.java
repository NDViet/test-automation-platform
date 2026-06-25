package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A GitHub repository the platform manages, discovered through an integration credential. */
@Entity
@Table(name = "github_managed_repos")
public class GithubManagedRepo {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "credential_id", nullable = false)
  private UUID credentialId;

  @Column(name = "full_name", nullable = false, length = 400)
  private String fullName;

  @Column(name = "owner", length = 200)
  private String owner;

  @Column(name = "name", length = 200)
  private String name;

  @Column(name = "private", nullable = false)
  private boolean isPrivate;

  @Column(name = "default_branch", length = 200)
  private String defaultBranch;

  @Column(name = "html_url", length = 500)
  private String htmlUrl;

  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt = Instant.now();

  protected GithubManagedRepo() {}

  public GithubManagedRepo(
      UUID credentialId,
      String fullName,
      String owner,
      String name,
      boolean isPrivate,
      String defaultBranch,
      String htmlUrl) {
    this.credentialId = credentialId;
    this.fullName = fullName;
    this.owner = owner;
    this.name = name;
    this.isPrivate = isPrivate;
    this.defaultBranch = defaultBranch;
    this.htmlUrl = htmlUrl;
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

  public String getName() {
    return name;
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

  public Instant getAddedAt() {
    return addedAt;
  }
}
