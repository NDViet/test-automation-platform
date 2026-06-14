package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An Azure DevOps iteration / sprint (classification node) synced into the platform. */
@Entity
@Table(name = "ado_iterations")
public class AdoIteration {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "ado_id")                        private String adoId;
    @Column(nullable = false)                       private String path;
    @Column(nullable = false)                       private String name;
    @Column(name = "parent_path")                   private String parentPath;
    @Column(name = "start_date")                    private LocalDate startDate;
    @Column(name = "finish_date")                   private LocalDate finishDate;
    @Column(name = "has_children", nullable = false) private boolean hasChildren;
    @Column(name = "synced_at", nullable = false)   private Instant syncedAt = Instant.now();

    protected AdoIteration() {}
    public AdoIteration(UUID projectId, String path, String name) {
        this.projectId = projectId; this.path = path; this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getAdoId() { return adoId; }
    public String getPath() { return path; }
    public String getName() { return name; }
    public String getParentPath() { return parentPath; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getFinishDate() { return finishDate; }
    public boolean isHasChildren() { return hasChildren; }
    public Instant getSyncedAt() { return syncedAt; }

    public void setAdoId(String v) { this.adoId = v; }
    public void setName(String v) { this.name = v; }
    public void setParentPath(String v) { this.parentPath = v; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public void setFinishDate(LocalDate v) { this.finishDate = v; }
    public void setHasChildren(boolean v) { this.hasChildren = v; }
    public void setSyncedAt(Instant v) { this.syncedAt = v; }
}
