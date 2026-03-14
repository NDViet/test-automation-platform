package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit trail record for security-sensitive and operationally
 * important platform events.
 *
 * <p>Event types: INGEST, API_KEY_CREATED, API_KEY_REVOKED,
 * TICKET_CREATED, TICKET_CLOSED, ANALYSIS_TRIGGERED, QUALITY_GATE_FAILED.</p>
 */
@Entity
@Table(name = "audit_events",
       indexes = {
           @Index(name = "idx_ae_event_type",  columnList = "event_type"),
           @Index(name = "idx_ae_actor_key_id", columnList = "actor_key_id"),
           @Index(name = "idx_ae_occurred_at",  columnList = "occurred_at"),
           @Index(name = "idx_ae_team_id",      columnList = "team_id")
       })
@EntityListeners(AuditingEntityListener.class)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** API key UUID of the caller (null for internal/system events). */
    @Column(name = "actor_key_id")
    private UUID actorKeyId;

    /** Key prefix shown for quick identification without exposing the hash. */
    @Column(name = "actor_key_prefix", length = 10)
    private String actorKeyPrefix;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 500)
    private String resourceId;

    /** JSON blob with event-specific details (run ID, test count, etc.). */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "outcome", length = 20)
    private String outcome; // SUCCESS, FAILURE, BLOCKED

    @CreatedDate
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {}

    private AuditEvent(Builder b) {
        this.eventType       = b.eventType;
        this.actorKeyId      = b.actorKeyId;
        this.actorKeyPrefix  = b.actorKeyPrefix;
        this.teamId          = b.teamId;
        this.resourceType    = b.resourceType;
        this.resourceId      = b.resourceId;
        this.details         = b.details;
        this.clientIp        = b.clientIp;
        this.outcome         = b.outcome;
    }

    public static Builder builder() { return new Builder(); }

    public UUID getId()               { return id; }
    public String getEventType()      { return eventType; }
    public UUID getActorKeyId()       { return actorKeyId; }
    public String getActorKeyPrefix() { return actorKeyPrefix; }
    public UUID getTeamId()           { return teamId; }
    public String getResourceType()   { return resourceType; }
    public String getResourceId()     { return resourceId; }
    public String getDetails()        { return details; }
    public String getClientIp()       { return clientIp; }
    public String getOutcome()        { return outcome; }
    public Instant getOccurredAt()    { return occurredAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditEvent a)) return false;
        return Objects.equals(id, a.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static class Builder {
        private String eventType;
        private UUID actorKeyId;
        private String actorKeyPrefix;
        private UUID teamId;
        private String resourceType;
        private String resourceId;
        private String details;
        private String clientIp;
        private String outcome;

        public Builder eventType(String v)      { this.eventType      = v; return this; }
        public Builder actorKeyId(UUID v)       { this.actorKeyId     = v; return this; }
        public Builder actorKeyPrefix(String v) { this.actorKeyPrefix = v; return this; }
        public Builder teamId(UUID v)           { this.teamId         = v; return this; }
        public Builder resourceType(String v)   { this.resourceType   = v; return this; }
        public Builder resourceId(String v)     { this.resourceId     = v; return this; }
        public Builder details(String v)        { this.details        = v; return this; }
        public Builder clientIp(String v)       { this.clientIp       = v; return this; }
        public Builder outcome(String v)        { this.outcome        = v; return this; }
        public AuditEvent build()               { return new AuditEvent(this); }
    }
}
