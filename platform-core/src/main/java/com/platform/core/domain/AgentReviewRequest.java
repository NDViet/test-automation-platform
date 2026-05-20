package com.platform.core.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "agent_review_requests")
@EntityListeners(AuditingEntityListener.class)
public class AgentReviewRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "destination", nullable = false, columnDefinition = "TEXT")
    private String destination;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "artifact_manifest", columnDefinition = "jsonb")
    private Map<String, Object> artifactManifest;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "checkpoint_id", length = 200)
    private String checkpointId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "decision", length = 20)
    private String decision;

    @Column(name = "decided_by", length = 200)
    private String decidedBy;

    @Column(name = "decision_payload", columnDefinition = "TEXT")
    private String decisionPayload;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "deferred_until")
    private Instant deferredUntil;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected AgentReviewRequest() {}

    public AgentReviewRequest(UUID workflowId, UUID stepId, String channel,
                               String destination, String summary, String checkpointId,
                               Map<String, Object> artifactManifest) {
        this.workflowId       = workflowId;
        this.stepId           = stepId;
        this.channel          = channel;
        this.destination      = destination;
        this.summary          = summary;
        this.checkpointId     = checkpointId;
        this.artifactManifest = artifactManifest;
        this.expiresAt        = Instant.now().plusSeconds(48 * 3600);
    }

    public void approve(String decidedBy) {
        this.status    = "APPROVED"; this.decision = "APPROVED";
        this.decidedBy = decidedBy; this.decidedAt = Instant.now();
    }
    public void reject(String decidedBy) {
        this.status    = "REJECTED"; this.decision = "REJECTED";
        this.decidedBy = decidedBy; this.decidedAt = Instant.now();
    }
    public void edit(String decidedBy, String editedPayload) {
        this.status          = "EDITED"; this.decision = "EDIT";
        this.decidedBy       = decidedBy; this.decisionPayload = editedPayload;
        this.decidedAt       = Instant.now();
    }
    public void defer(Instant until) {
        this.status        = "DEFERRED"; this.deferredUntil = until;
        this.expiresAt     = until.plusSeconds(48 * 3600);
    }

    public UUID getId()              { return id; }
    public UUID getWorkflowId()      { return workflowId; }
    public UUID getStepId()          { return stepId; }
    public String getChannel()       { return channel; }
    public String getDestination()   { return destination; }
    public Map<String, Object> getArtifactManifest() { return artifactManifest; }
    public String getSummary()       { return summary; }
    public String getCheckpointId()  { return checkpointId; }
    public String getStatus()        { return status; }
    public String getDecision()      { return decision; }
    public String getDecidedBy()     { return decidedBy; }
    public String getDecisionPayload() { return decisionPayload; }
    public Instant getExpiresAt()    { return expiresAt; }
    public Instant getDeferredUntil(){ return deferredUntil; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getDecidedAt()    { return decidedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentReviewRequest r)) return false;
        return Objects.equals(id, r.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
