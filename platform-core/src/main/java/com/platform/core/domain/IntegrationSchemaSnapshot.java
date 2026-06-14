package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A baseline snapshot of an upstream work-item-type schema, used for drift detection.
 * Comparing the current live schema against this baseline yields removed/added/
 * type-changed fields and state-category changes. Re-capturing = accept current schema.
 */
@Entity
@Table(name = "integration_schema_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uq_schema_snapshot",
                columnNames = {"project_id", "integration_type", "ado_project", "work_item_type"}))
@EntityListeners(AuditingEntityListener.class)
public class IntegrationSchemaSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "integration_type", nullable = false, length = 64)
    private String integrationType;

    @Column(name = "ado_project", nullable = false, length = 200)
    private String adoProject;

    @Column(name = "work_item_type", nullable = false, length = 200)
    private String workItemType;

    @Column(name = "fields_json", nullable = false, columnDefinition = "text")
    private String fieldsJson;

    @Column(name = "state_categories_json", nullable = false, columnDefinition = "text")
    private String stateCategoriesJson;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "captured_by", length = 100)
    private String capturedBy;

    @CreatedDate
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IntegrationSchemaSnapshot() {}

    public IntegrationSchemaSnapshot(UUID projectId, String integrationType, String adoProject,
                                     String workItemType, String fieldsJson, String stateCategoriesJson,
                                     String fingerprint, String capturedBy) {
        this.projectId = projectId;
        this.integrationType = integrationType;
        this.adoProject = adoProject;
        this.workItemType = workItemType;
        this.fieldsJson = fieldsJson;
        this.stateCategoriesJson = stateCategoriesJson;
        this.fingerprint = fingerprint;
        this.capturedBy = capturedBy;
    }

    public UUID getId()                  { return id; }
    public UUID getProjectId()           { return projectId; }
    public String getIntegrationType()   { return integrationType; }
    public String getAdoProject()        { return adoProject; }
    public String getWorkItemType()      { return workItemType; }
    public String getFieldsJson()        { return fieldsJson; }
    public String getStateCategoriesJson(){ return stateCategoriesJson; }
    public String getFingerprint()       { return fingerprint; }
    public String getCapturedBy()        { return capturedBy; }
    public Instant getCapturedAt()       { return capturedAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public void update(String fieldsJson, String stateCategoriesJson, String fingerprint, String capturedBy) {
        this.fieldsJson = fieldsJson;
        this.stateCategoriesJson = stateCategoriesJson;
        this.fingerprint = fingerprint;
        this.capturedBy = capturedBy;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegrationSchemaSnapshot s)) return false;
        return Objects.equals(id, s.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
