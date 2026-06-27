package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Settings for a passphrase-derived credential encryption key (a single row). Used only when {@code
 * PLATFORM_CRED_KEY} is not provided via the environment: the AES-256 key is derived from an
 * admin-chosen passphrase via PBKDF2. We persist only the {@code salt}, {@code iterations}, and a
 * {@code verifier} (a known constant encrypted with the derived key) — never the key or passphrase.
 * The verifier confirms a re-entered passphrase is correct when unlocking after a restart.
 */
@Entity
@Table(name = "cred_key_settings")
public class CredKeySetting {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "salt", nullable = false, length = 64)
  private String salt;

  @Column(name = "iterations", nullable = false)
  private int iterations;

  @Column(name = "verifier", nullable = false, length = 256)
  private String verifier;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected CredKeySetting() {}

  public CredKeySetting(String salt, int iterations, String verifier) {
    this.salt = salt;
    this.iterations = iterations;
    this.verifier = verifier;
  }

  public UUID getId() {
    return id;
  }

  public String getSalt() {
    return salt;
  }

  public int getIterations() {
    return iterations;
  }

  public String getVerifier() {
    return verifier;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
