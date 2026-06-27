package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * An Azure DevOps organization the platform manages, discovered through an integration credential.
 * A single PAT can span multiple orgs; this records the subset the user chose to manage. Mirrors
 * {@link GithubManagedRepo}.
 */
@Entity
@Table(name = "azure_managed_orgs")
public class AzureManagedOrg {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "credential_id", nullable = false)
  private UUID credentialId;

  @Column(name = "account_name", nullable = false, length = 200)
  private String accountName;

  @Column(name = "account_id", length = 64)
  private String accountId;

  @Column(name = "account_uri", length = 500)
  private String accountUri;

  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt = Instant.now();

  protected AzureManagedOrg() {}

  public AzureManagedOrg(
      UUID credentialId, String accountName, String accountId, String accountUri) {
    this.credentialId = credentialId;
    this.accountName = accountName;
    this.accountId = accountId;
    this.accountUri = accountUri;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCredentialId() {
    return credentialId;
  }

  public String getAccountName() {
    return accountName;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getAccountUri() {
    return accountUri;
  }

  public Instant getAddedAt() {
    return addedAt;
  }
}
