package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A role grant for a user at a scope: {@code ORG_ADMIN} at ORG scope, or {@code
 * PROJECT_ADMIN/TESTER/VIEWER} at PROJECT scope. {@code scopeId} is the org id or project id.
 */
@Entity
@Table(name = "user_roles")
public class UserRole {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** ORG_ADMIN | PROJECT_ADMIN | TESTER | VIEWER */
  @Column(name = "role", nullable = false, length = 20)
  private String role;

  /** ORG | PROJECT */
  @Column(name = "scope", nullable = false, length = 10)
  private String scope;

  @Column(name = "scope_id", nullable = false)
  private UUID scopeId;

  @Column(name = "created_by", length = 200)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected UserRole() {}

  public UserRole(UUID userId, String role, String scope, UUID scopeId, String createdBy) {
    this.userId = userId;
    this.role = role;
    this.scope = scope;
    this.scopeId = scopeId;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getRole() {
    return role;
  }

  public String getScope() {
    return scope;
  }

  public UUID getScopeId() {
    return scopeId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserRole r)) return false;
    return Objects.equals(id, r.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
