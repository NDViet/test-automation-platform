package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A platform user with password authentication. Super-admins ({@code isSuperAdmin}) bypass scope
 * checks; everyone else is authorized via {@link UserRole} grants. The bootstrap super-admin is
 * created on first start with {@code mustChangePassword = true}.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "username", nullable = false, unique = true, length = 100)
  private String username;

  @Column(name = "email", length = 200)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 200)
  private String passwordHash;

  @Column(name = "display_name", length = 200)
  private String displayName;

  @Column(name = "is_super_admin", nullable = false)
  private boolean superAdmin = false;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  protected User() {}

  public User(
      String username,
      String email,
      String passwordHash,
      String displayName,
      boolean superAdmin,
      boolean mustChangePassword) {
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.superAdmin = superAdmin;
    this.mustChangePassword = mustChangePassword;
  }

  /** Set a new password hash and clear the must-change flag. */
  public void changePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.mustChangePassword = false;
    this.updatedAt = Instant.now();
  }

  /** Admin-initiated reset: set a new hash and force the user to change it on next login. */
  public void resetPasswordByAdmin(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.mustChangePassword = true;
    this.updatedAt = Instant.now();
  }

  public void recordLogin() {
    this.lastLoginAt = Instant.now();
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isSuperAdmin() {
    return superAdmin;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User u)) return false;
    return Objects.equals(id, u.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
