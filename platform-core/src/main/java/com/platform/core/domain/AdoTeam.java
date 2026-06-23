package com.platform.core.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An Azure DevOps team synced into the platform (owns area paths inside a project). */
@Entity
@Table(name = "ado_teams")
public class AdoTeam {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "ado_id", nullable = false)      private String adoId;
    @Column(nullable = false)                        private String name;
    @Column(columnDefinition = "TEXT")              private String description;
    @Column(name = "default_area_path")             private String defaultAreaPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "area_paths", columnDefinition = "jsonb")
    private List<String> areaPaths = new java.util.ArrayList<>();

    @Column(name = "slug", length = 100)              private String slug;
    @Column(name = "member_count", nullable = false) private int memberCount = 0;
    @Column(name = "synced_at", nullable = false)    private Instant syncedAt = Instant.now();

    protected AdoTeam() {}
    public AdoTeam(UUID projectId, String adoId, String name) {
        this.projectId = projectId; this.adoId = adoId; this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getAdoId() { return adoId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getDefaultAreaPath() { return defaultAreaPath; }
    public List<String> getAreaPaths() { return areaPaths; }
    public int getMemberCount() { return memberCount; }
    public Instant getSyncedAt() { return syncedAt; }

    public void setName(String v) { this.name = v; }
    public void setSlug(String v) { this.slug = v; }
    public void setDescription(String v) { this.description = v; }
    public void setDefaultAreaPath(String v) { this.defaultAreaPath = v; }
    public void setAreaPaths(List<String> v) { this.areaPaths = v != null ? v : new java.util.ArrayList<>(); }
    public void setMemberCount(int v) { this.memberCount = v; }
    public void setSyncedAt(Instant v) { this.syncedAt = v; }
}
