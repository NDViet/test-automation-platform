package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An RBAC role assignment. A null {@code teamId} denotes an organization-wide
 * role ({@code ORG_ADMIN} or org {@code VIEWER}); otherwise the role applies to
 * the referenced team.
 */
@Entity
@Table(name = "team_members")
public class TeamMember {

    public enum Role { ORG_ADMIN, TEAM_ADMIN, TEAM_MEMBER, VIEWER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 200)
    private String userId;

    /** Null = organization-wide role. */
    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by", length = 200)
    private String grantedBy;

    protected TeamMember() {}

    public TeamMember(String userId, UUID teamId, Role role, String grantedBy) {
        this.userId    = userId;
        this.teamId    = teamId;
        this.role      = role.name();
        this.grantedBy = grantedBy;
    }

    public UUID getId()        { return id; }
    public String getUserId()  { return userId; }
    public UUID getTeamId()    { return teamId; }
    public String getRole()    { return role; }
    public Instant getGrantedAt() { return grantedAt; }
    public String getGrantedBy()  { return grantedBy; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMember m)) return false;
        return Objects.equals(id, m.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
