package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Assignment of a GitHub repo to a project with a role (General / Codebase / Test Automation). */
@Entity
@Table(name = "project_repo_assignments",
       indexes = @Index(name = "idx_pra_project_id", columnList = "project_id"))
public class ProjectRepoAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "repo_full_name", nullable = false, length = 400)
    private String repoFullName;

    @Column(name = "role", nullable = false, length = 30)
    private String role;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    protected ProjectRepoAssignment() {}

    public ProjectRepoAssignment(UUID projectId, UUID credentialId, String repoFullName, String role) {
        this.projectId    = projectId;
        this.credentialId = credentialId;
        this.repoFullName = repoFullName;
        this.role         = role != null ? role : "GENERAL";
    }

    public UUID getId()             { return id; }
    public UUID getProjectId()      { return projectId; }
    public UUID getCredentialId()   { return credentialId; }
    public String getRepoFullName() { return repoFullName; }
    public String getRole()         { return role; }
    public Instant getAddedAt()     { return addedAt; }

    public void setRole(String role) { this.role = role; }
}
