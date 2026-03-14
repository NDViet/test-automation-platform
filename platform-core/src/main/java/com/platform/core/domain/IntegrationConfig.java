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

/**
 * Per-team issue tracker configuration (JIRA, LINEAR, GITHUB).
 *
 * <p>{@code configJson} holds tracker-specific fields such as
 * {@code email}, {@code apiToken}, {@code minConsecutiveFailures},
 * {@code flakinessThreshold}, {@code doneTransitionName}, etc.</p>
 */
@Entity
@Table(name = "integration_configs",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_integration_team_tracker",
               columnNames = {"team_id", "tracker_type"}))
@EntityListeners(AuditingEntityListener.class)
public class IntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "tracker_type", nullable = false, length = 20)
    private String trackerType; // JIRA, LINEAR, GITHUB

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "project_key", length = 50)
    private String projectKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> configJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IntegrationConfig() {}

    public IntegrationConfig(UUID teamId, String trackerType, String baseUrl,
                              String projectKey, Map<String, String> configJson) {
        this.teamId      = teamId;
        this.trackerType = trackerType;
        this.baseUrl     = baseUrl;
        this.projectKey  = projectKey;
        this.configJson  = configJson;
    }

    public UUID getId()                       { return id; }
    public UUID getTeamId()                   { return teamId; }
    public String getTrackerType()            { return trackerType; }
    public String getBaseUrl()                { return baseUrl; }
    public String getProjectKey()             { return projectKey; }
    public Map<String, String> getConfigJson(){ return configJson; }
    public boolean isEnabled()                { return enabled; }
    public Instant getCreatedAt()             { return createdAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String config(String key) {
        return configJson != null ? configJson.get(key) : null;
    }

    public String config(String key, String defaultValue) {
        String v = config(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegrationConfig c)) return false;
        return Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
