package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted mapping-rule override for the Mapping Suggester, scoped to an
 * organization or a project. Resolution precedence (most specific wins):
 * <b>PROJECT → ORG → built-in default file</b>. Deleting the row "resets to default"
 * (resolution falls back to the parent scope).
 */
@Entity
@Table(name = "mapping_rulesets",
        uniqueConstraints = @UniqueConstraint(name = "uq_mapping_ruleset_scope", columnNames = {"scope", "scope_id"}))
@EntityListeners(AuditingEntityListener.class)
public class MappingRuleset {

    public enum Scope { ORG, PROJECT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "rules_json", nullable = false, columnDefinition = "text")
    private String rulesJson;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MappingRuleset() {}

    public MappingRuleset(Scope scope, UUID scopeId, String rulesJson, String updatedBy) {
        this.scope = scope.name();
        this.scopeId = scopeId;
        this.rulesJson = rulesJson;
        this.updatedBy = updatedBy;
    }

    public UUID getId()         { return id; }
    public String getScope()    { return scope; }
    public UUID getScopeId()    { return scopeId; }
    public String getRulesJson(){ return rulesJson; }
    public String getUpdatedBy(){ return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MappingRuleset m)) return false;
        return Objects.equals(id, m.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
