package com.platform.analytics.alerts;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a fired alert.
 */
public record AlertEvent(
        String id,
        AlertRule.Severity severity,
        String ruleName,
        String message,
        String teamId,
        String projectId,
        String runId,
        Instant firedAt
) {
    public static AlertEvent of(AlertRule rule, String message,
                                String teamId, String projectId, String runId) {
        return new AlertEvent(
                UUID.randomUUID().toString(),
                rule.severity(),
                rule.name(),
                message,
                teamId, projectId, runId,
                Instant.now()
        );
    }
}
