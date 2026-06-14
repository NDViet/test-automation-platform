package com.platform.core.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A scoped, encrypted integration credential.
 *
 * <p>One row per (scope, scopeId, integrationType). {@code scope} is one of
 * {@code ORG} / {@code TEAM} / {@code PROJECT}; {@code scopeId} is NULL for ORG,
 * the team id for TEAM, or the project id for PROJECT. Resolution deep-merges
 * the three scopes with precedence PROJECT &gt; TEAM &gt; ORG (see
 * {@code CredentialResolver}).</p>
 *
 * <p>{@code connectionParams} holds NON-secret fields (org, project_key,
 * area_path, repo, owner, etc.). {@code secretCiphertext} holds the AES-GCM
 * encrypted JSON of secret fields (e.g. {@code {"pat":"...","clientSecret":"..."}}).</p>
 */
@Entity
@Table(name = "integration_credentials")
@EntityListeners(AuditingEntityListener.class)
public class IntegrationCredential {

    public enum Scope { ORG, TEAM, PROJECT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scope", nullable = false, length = 10)
    private String scope;

    /** NULL for ORG scope; team id or project id otherwise. */
    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(name = "integration_type", nullable = false, length = 40)
    private String integrationType;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "connection_params", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> connectionParams;

    /** AES-GCM encrypted JSON of secret fields; null if this scope contributes no secret. */
    @Column(name = "secret_ciphertext", columnDefinition = "text")
    private String secretCiphertext;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IntegrationCredential() {}

    public IntegrationCredential(Scope scope, UUID scopeId, String integrationType,
                                  String displayName, String baseUrl,
                                  Map<String, String> connectionParams,
                                  String secretCiphertext) {
        this.scope            = scope.name();
        this.scopeId          = scopeId;
        this.integrationType  = integrationType;
        this.displayName      = displayName;
        this.baseUrl          = baseUrl;
        this.connectionParams = connectionParams;
        this.secretCiphertext = secretCiphertext;
    }

    public UUID getId()                        { return id; }
    public String getScope()                   { return scope; }
    public UUID getScopeId()                   { return scopeId; }
    public String getIntegrationType()         { return integrationType; }
    public String getDisplayName()             { return displayName; }
    public String getBaseUrl()                 { return baseUrl; }
    public Map<String, String> getConnectionParams() { return connectionParams; }
    public String getSecretCiphertext()        { return secretCiphertext; }
    public boolean isEnabled()                 { return enabled; }
    public String getCreatedBy()               { return createdBy; }
    public Instant getCreatedAt()              { return createdAt; }
    public Instant getUpdatedAt()              { return updatedAt; }

    public void setDisplayName(String v)       { this.displayName = v; }
    public void setBaseUrl(String v)           { this.baseUrl = v; }
    public void setConnectionParams(Map<String, String> v) { this.connectionParams = v; }
    public void setSecretCiphertext(String v)  { this.secretCiphertext = v; }
    public void setEnabled(boolean v)          { this.enabled = v; }
    public void setCreatedBy(String v)         { this.createdBy = v; }

    public String param(String key) {
        return connectionParams != null ? connectionParams.get(key) : null;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegrationCredential c)) return false;
        return Objects.equals(id, c.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
