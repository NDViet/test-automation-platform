package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A hashed API key issued to a team for authenticating requests.
 *
 * <p>The raw key is shown once at creation and never stored. Only the
 * SHA-256 hash is persisted for secure lookup.</p>
 */
@Entity
@Table(name = "api_keys",
       indexes = {
           @Index(name = "idx_ak_key_hash", columnList = "key_hash", unique = true),
           @Index(name = "idx_ak_team_id",  columnList = "team_id")
       })
@EntityListeners(AuditingEntityListener.class)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-readable label (e.g. "team-payments CI pipeline"). */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** SHA-256 hex hash of the raw key. Never store the raw key. */
    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    /** First 8 characters of the raw key for identification (safe to display). */
    @Column(name = "key_prefix", nullable = false, length = 10)
    private String keyPrefix;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApiKey() {}

    private ApiKey(Builder b) {
        this.name      = b.name;
        this.keyHash   = b.keyHash;
        this.keyPrefix = b.keyPrefix;
        this.teamId    = b.teamId;
        this.revoked   = false;
        this.expiresAt = b.expiresAt;
    }

    public static Builder builder() { return new Builder(); }

    public UUID getId()          { return id; }
    public String getName()      { return name; }
    public String getKeyHash()   { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public UUID getTeamId()      { return teamId; }
    public boolean isRevoked()   { return revoked; }
    public Instant getExpiresAt(){ return expiresAt; }
    public Instant getLastUsedAt(){ return lastUsedAt; }
    public Instant getCreatedAt(){ return createdAt; }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !revoked && !isExpired();
    }

    public void revoke() { this.revoked = true; }

    public void recordUsage() { this.lastUsedAt = Instant.now(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiKey k)) return false;
        return Objects.equals(id, k.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static class Builder {
        private String name;
        private String keyHash;
        private String keyPrefix;
        private UUID teamId;
        private Instant expiresAt;

        public Builder name(String v)      { this.name = v;      return this; }
        public Builder keyHash(String v)   { this.keyHash = v;   return this; }
        public Builder keyPrefix(String v) { this.keyPrefix = v; return this; }
        public Builder teamId(UUID v)      { this.teamId = v;    return this; }
        public Builder expiresAt(Instant v){ this.expiresAt = v; return this; }
        public ApiKey build()              { return new ApiKey(this); }
    }
}
