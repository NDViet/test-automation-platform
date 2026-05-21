package com.platform.core.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Manual or agent-generated test case in the knowledge graph. */
@Entity
@Table(name = "platform_test_cases")
public class PlatformTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "integration_config_id")
    private UUID integrationConfigId;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ac_refs", columnDefinition = "jsonb")
    private List<String> acRefs;

    @Column(name = "coverage_status", nullable = false, length = 20)
    private String coverageStatus = "ACTIVE"; // ACTIVE | NEEDS_UPDATE | OBSOLETE | ARCHIVED

    @Column(name = "created_by", nullable = false, length = 10)
    private String createdBy = "HUMAN"; // AGENT | HUMAN

    @Column(name = "agent_session_id")
    private UUID agentSessionId;

    @Column(name = "last_result", length = 20)
    private String lastResult;

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @Column(name = "has_automation", nullable = false)
    private boolean hasAutomation = false;

    // TCM fields (added by V38)
    @Column(name = "suite_id")
    private UUID suiteId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT, UNDER_REVIEW, APPROVED, DEPRECATED

    @Column(name = "priority", nullable = false, length = 10)
    private String priority = "MEDIUM"; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "preconditions", columnDefinition = "TEXT")
    private String preconditions;

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "source_requirement_id")
    private UUID sourceRequirementId;

    @Column(name = "automation_status", nullable = false, length = 20)
    private String automationStatus = "NOT_STARTED"; // NOT_STARTED, GENERATING, PR_CREATED, PR_MERGED, FAILED

    @Column(name = "automation_pr_url", columnDefinition = "TEXT")
    private String automationPrUrl;

    @Column(name = "automation_github_config_id")
    private UUID automationGithubConfigId;

    @Column(name = "automation_workflow_id")
    private UUID automationWorkflowId;

    /** All requirements this test case covers (includes sourceRequirementId + manually added). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_requirement_ids", columnDefinition = "jsonb")
    private List<String> linkedRequirementIds = new java.util.ArrayList<>();

    /** ImpactAnalysis that last modified the body of this test case. */
    @Column(name = "last_updated_by_analysis_id")
    private UUID lastUpdatedByAnalysisId;

    /** HUMAN | AGENT | IMPACT_ANALYSIS */
    @Column(name = "updated_by", nullable = false, length = 30)
    private String updatedBy = "HUMAN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlatformTestCase() {}

    public PlatformTestCase(UUID projectId, String title, List<String> acRefs, String createdBy, UUID agentSessionId) {
        this.projectId       = projectId;
        this.title           = title;
        this.acRefs          = acRefs != null ? acRefs : List.of();
        this.createdBy       = createdBy;
        this.agentSessionId  = agentSessionId;
    }

    public void markNeedsUpdate()  { this.coverageStatus = "NEEDS_UPDATE"; this.updatedAt = Instant.now(); }
    public void markObsolete()     { this.coverageStatus = "OBSOLETE";     this.updatedAt = Instant.now(); }
    public void markActive()       { this.coverageStatus = "ACTIVE";       this.updatedAt = Instant.now(); }

    public UUID getId()              { return id; }
    public UUID getProjectId()       { return projectId; }
    public String getExternalId()    { return externalId; }
    public String getTitle()         { return title; }
    public List<String> getAcRefs()  { return acRefs; }
    public String getCoverageStatus(){ return coverageStatus; }
    public String getCreatedBy()     { return createdBy; }
    public UUID getAgentSessionId()  { return agentSessionId; }
    public String getLastResult()      { return lastResult; }
    public Instant getLastExecutedAt() { return lastExecutedAt; }
    public boolean isHasAutomation()   { return hasAutomation; }
    public Instant getCreatedAt()      { return createdAt; }
    public Instant getUpdatedAt()      { return updatedAt; }

    public void setTitle(String v)           { this.title = v;        this.updatedAt = Instant.now(); }
    public void setHasAutomation(boolean v) { this.hasAutomation = v; this.updatedAt = Instant.now(); }
    public void setAcRefs(List<String> v)   { this.acRefs = v;        this.updatedAt = Instant.now(); }

    // TCM getters
    public UUID getSuiteId()                  { return suiteId; }
    public String getStatus()                 { return status; }
    public String getPriority()               { return priority; }
    public String getDescription()            { return description; }
    public String getPreconditions()          { return preconditions; }
    public String getExpectedResult()         { return expectedResult; }
    public UUID getSourceRequirementId()      { return sourceRequirementId; }
    public String getAutomationStatus()       { return automationStatus; }
    public String getAutomationPrUrl()        { return automationPrUrl; }
    public UUID getAutomationGithubConfigId()      { return automationGithubConfigId; }
    public UUID getAutomationWorkflowId()           { return automationWorkflowId; }
    public List<String> getLinkedRequirementIds()   { return linkedRequirementIds; }
    public UUID getLastUpdatedByAnalysisId()        { return lastUpdatedByAnalysisId; }
    public String getUpdatedBy()                    { return updatedBy; }

    // TCM setters
    public void setSuiteId(UUID suiteId) {
        this.suiteId   = suiteId;
        this.updatedAt = Instant.now();
    }
    public void setStatus(String status) {
        this.status    = status;
        this.updatedAt = Instant.now();
    }
    public void setPriority(String priority) {
        this.priority  = priority;
        this.updatedAt = Instant.now();
    }
    public void setDescription(String description) {
        this.description = description;
        this.updatedAt   = Instant.now();
    }
    public void setPreconditions(String preconditions) {
        this.preconditions = preconditions;
        this.updatedAt     = Instant.now();
    }
    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
        this.updatedAt      = Instant.now();
    }
    public void setSourceRequirementId(UUID sourceRequirementId) {
        this.sourceRequirementId = sourceRequirementId;
        this.updatedAt           = Instant.now();
    }
    public void setAutomationPrUrl(String automationPrUrl) {
        this.automationPrUrl = automationPrUrl;
        this.updatedAt       = Instant.now();
    }
    public void setAutomationGithubConfigId(UUID automationGithubConfigId) {
        this.automationGithubConfigId = automationGithubConfigId;
        this.updatedAt                = Instant.now();
    }
    public void setAutomationWorkflowId(UUID workflowId) {
        this.automationWorkflowId = workflowId;
        this.updatedAt            = Instant.now();
    }

    public void linkRequirement(UUID requirementId) {
        if (requirementId == null) return;
        String reqStr = requirementId.toString();
        if (this.linkedRequirementIds == null) this.linkedRequirementIds = new java.util.ArrayList<>();
        if (!this.linkedRequirementIds.contains(reqStr)) {
            this.linkedRequirementIds = new java.util.ArrayList<>(this.linkedRequirementIds);
            this.linkedRequirementIds.add(reqStr);
            this.updatedAt = Instant.now();
        }
    }

    public void unlinkRequirement(UUID requirementId) {
        if (requirementId == null || this.linkedRequirementIds == null) return;
        String reqStr = requirementId.toString();
        this.linkedRequirementIds = this.linkedRequirementIds.stream()
                .filter(id -> !id.equals(reqStr))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        this.updatedAt = Instant.now();
    }

    /** Called when an impact analysis suggestion is applied to this test case. */
    public void applyAnalysisSuggestion(UUID analysisId) {
        this.lastUpdatedByAnalysisId = analysisId;
        this.updatedBy               = "IMPACT_ANALYSIS";
        this.status                  = "UNDER_REVIEW";
        this.updatedAt               = Instant.now();
    }

    // TCM lifecycle methods
    public void submitForReview() { this.status = "UNDER_REVIEW"; this.updatedBy = "HUMAN"; this.updatedAt = Instant.now(); }
    public void approve()         { this.status = "APPROVED";     this.updatedBy = "HUMAN"; this.updatedAt = Instant.now(); }
    public void reject()          { this.status = "DRAFT";        this.updatedBy = "HUMAN"; this.updatedAt = Instant.now(); }
    public void deprecate()       { this.status = "DEPRECATED";   this.updatedBy = "HUMAN"; this.updatedAt = Instant.now(); }

    // Automation lifecycle methods
    public void markAutomationGenerating() {
        this.automationStatus = "GENERATING";
        this.hasAutomation    = false;
        this.updatedAt        = Instant.now();
    }
    public void markAutomationPrCreated(String prUrl) {
        this.automationStatus = "PR_CREATED";
        this.automationPrUrl  = prUrl;
        this.hasAutomation    = true;
        this.updatedAt        = Instant.now();
    }
    public void markAutomationFailed() {
        this.automationStatus = "FAILED";
        this.updatedAt        = Instant.now();
    }
    public void markAutomationMerged() {
        this.automationStatus = "PR_MERGED";
        this.hasAutomation    = true;
        this.updatedAt        = Instant.now();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlatformTestCase t)) return false;
        return Objects.equals(id, t.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
