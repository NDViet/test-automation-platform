package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A team <em>within</em> a project — aligns with an Azure DevOps team (owns area paths / a backlog
 * inside a project). ADO-first hierarchy: <b>Organization → Project → Team</b>.
 */
@Entity
@Table(
    name = "teams",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_team_project_slug",
            columnNames = {"project_id", "slug"}))
@EntityListeners(AuditingEntityListener.class)
public class Team {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "slug", nullable = false, length = 50)
  private String slug;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Team() {}

  public Team(UUID projectId, String name, String slug) {
    this.projectId = projectId;
    this.name = name;
    this.slug = slug;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Team t)) return false;
    return Objects.equals(id, t.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
