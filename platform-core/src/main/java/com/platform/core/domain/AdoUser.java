package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A person in the project's ADO context — sourced from ADO team membership and/or
 * referenced on work items (assignee / creator / changer). The directory other quality
 * dashboards attribute activity to.
 */
@Entity
@Table(name = "ado_users")
public class AdoUser {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)  private UUID projectId;
    @Column(name = "unique_name", nullable = false)  private String uniqueName;
    @Column(name = "display_name")                   private String displayName;
    @Column                                          private String email;
    @Column                                          private String descriptor;
    @Column(name = "is_team_member", nullable = false)     private boolean teamMember;
    @Column(name = "seen_on_work_items", nullable = false) private boolean seenOnWorkItems;
    /** Platform annotation: QA | QE | SDET (null = not flagged as quality). Survives re-sync. */
    @Column(name = "quality_role", length = 20)      private String qualityRole;
    @Column(name = "synced_at", nullable = false)    private Instant syncedAt = Instant.now();

    protected AdoUser() {}
    public AdoUser(UUID projectId, String uniqueName, String displayName) {
        this.projectId = projectId; this.uniqueName = uniqueName; this.displayName = displayName;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getUniqueName() { return uniqueName; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getDescriptor() { return descriptor; }
    public boolean isTeamMember() { return teamMember; }
    public boolean isSeenOnWorkItems() { return seenOnWorkItems; }
    public String getQualityRole() { return qualityRole; }
    public Instant getSyncedAt() { return syncedAt; }

    public void setQualityRole(String v) { this.qualityRole = v; }

    public void setDisplayName(String v) { this.displayName = v; }
    public void setEmail(String v) { this.email = v; }
    public void setDescriptor(String v) { this.descriptor = v; }
    public void setTeamMember(boolean v) { this.teamMember = v; }
    public void setSeenOnWorkItems(boolean v) { this.seenOnWorkItems = v; }
    public void setSyncedAt(Instant v) { this.syncedAt = v; }
}
