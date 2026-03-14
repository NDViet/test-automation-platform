package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "projects",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "slug"}))
@EntityListeners(AuditingEntityListener.class)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, length = 50)
    private String slug;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {}

    public Project(Team team, String name, String slug) {
        this.team = team;
        this.name = name;
        this.slug = slug;
    }

    public UUID getId() { return id; }
    public Team getTeam() { return team; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getRepoUrl() { return repoUrl; }
    public Instant getCreatedAt() { return createdAt; }

    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Project p)) return false;
        return Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
