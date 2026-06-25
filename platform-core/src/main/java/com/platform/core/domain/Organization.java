package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Top-level tenant — aligns with an Azure DevOps <em>organization</em> (e.g. {@code contoso}).
 * ADO-first hierarchy: <b>Organization → Project → Team</b>.
 */
@Entity
@Table(name = "organizations")
@EntityListeners(AuditingEntityListener.class)
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, unique = true, length = 100)
  private String name;

  @Column(name = "slug", nullable = false, unique = true, length = 50)
  private String slug;

  @Column(name = "display_name", length = 100)
  private String displayName;

  @Column(name = "logo_key", length = 255)
  private String logoKey;

  @Column(name = "logo_content_type", length = 100)
  private String logoContentType;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Organization() {}

  public Organization(String name, String slug) {
    this.name = name;
    this.slug = slug;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getLogoKey() {
    return logoKey;
  }

  public String getLogoContentType() {
    return logoContentType;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setLogoKey(String logoKey) {
    this.logoKey = logoKey;
  }

  public void setLogoContentType(String logoContentType) {
    this.logoContentType = logoContentType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Organization t)) return false;
    return Objects.equals(id, t.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
