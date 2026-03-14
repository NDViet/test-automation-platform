package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted record of every alert event that was fired by the rule engine.
 * Used for audit, dashboards, and deduplication.
 */
@Entity
@Table(name = "alert_history",
       indexes = {
           @Index(name = "idx_ah_project_id", columnList = "project_id"),
           @Index(name = "idx_ah_fired_at",   columnList = "fired_at"),
           @Index(name = "idx_ah_severity",   columnList = "severity")
       })
@EntityListeners(AuditingEntityListener.class)
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "team_id", length = 200)
    private String teamId;

    @Column(name = "project_id", length = 200)
    private String projectId;

    @Column(name = "run_id", length = 200)
    private String runId;

    /** Which channel(s) the notification was sent to, comma-separated. */
    @Column(name = "channels", length = 200)
    private String channels;

    @Column(name = "delivered", nullable = false)
    private boolean delivered;

    @CreatedDate
    @Column(name = "fired_at", nullable = false, updatable = false)
    private Instant firedAt;

    protected AlertHistory() {}

    private AlertHistory(Builder b) {
        this.ruleName  = b.ruleName;
        this.severity  = b.severity;
        this.message   = b.message;
        this.teamId    = b.teamId;
        this.projectId = b.projectId;
        this.runId     = b.runId;
        this.channels  = b.channels;
        this.delivered = b.delivered;
    }

    public static Builder builder() { return new Builder(); }

    public UUID getId()        { return id; }
    public String getRuleName(){ return ruleName; }
    public String getSeverity(){ return severity; }
    public String getMessage() { return message; }
    public String getTeamId()  { return teamId; }
    public String getProjectId(){ return projectId; }
    public String getRunId()   { return runId; }
    public String getChannels(){ return channels; }
    public boolean isDelivered(){ return delivered; }
    public Instant getFiredAt(){ return firedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertHistory a)) return false;
        return Objects.equals(id, a.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static class Builder {
        private String ruleName;
        private String severity;
        private String message;
        private String teamId;
        private String projectId;
        private String runId;
        private String channels;
        private boolean delivered;

        public Builder ruleName(String v)  { this.ruleName  = v; return this; }
        public Builder severity(String v)  { this.severity  = v; return this; }
        public Builder message(String v)   { this.message   = v; return this; }
        public Builder teamId(String v)    { this.teamId    = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder runId(String v)     { this.runId     = v; return this; }
        public Builder channels(String v)  { this.channels  = v; return this; }
        public Builder delivered(boolean v){ this.delivered = v; return this; }
        public AlertHistory build()        { return new AlertHistory(this); }
    }
}
