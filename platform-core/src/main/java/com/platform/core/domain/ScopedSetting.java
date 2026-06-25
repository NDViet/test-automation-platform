package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A per-team or per-project setting override. ORG-level defaults live in {@link PlatformSetting};
 * {@code CredentialResolver}'s sibling {@code SettingResolver} merges PROJECT &gt; TEAM &gt; ORG.
 */
@Entity
@Table(
    name = "scoped_settings",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_ss_scope_key",
            columnNames = {"scope", "scope_id", "key"}))
public class ScopedSetting {

  public enum Scope {
    TEAM,
    PROJECT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "scope", nullable = false, length = 10)
  private String scope;

  @Column(name = "scope_id", nullable = false)
  private UUID scopeId;

  @Column(name = "key", nullable = false, length = 200)
  private String key;

  @Column(name = "value", columnDefinition = "text")
  private String value;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected ScopedSetting() {}

  public ScopedSetting(Scope scope, UUID scopeId, String key, String value) {
    this.scope = scope.name();
    this.scopeId = scopeId;
    this.key = key;
    this.value = value;
  }

  public UUID getId() {
    return id;
  }

  public String getScope() {
    return scope;
  }

  public UUID getScopeId() {
    return scopeId;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setValue(String value) {
    this.value = value;
    this.updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ScopedSetting s)) return false;
    return Objects.equals(id, s.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
