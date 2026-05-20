package com.platform.common.agent;

import com.platform.common.integration.IntegrationType;

import java.time.Instant;

/**
 * What triggered the agent session — webhook, schedule, or human request.
 */
public record TriggerRef(
        TriggerType triggerType,
        IntegrationType source,
        String entityType,       // "pull_request", "issue", "schedule", "manual"
        String entityExternalId, // PR number, issue key, schedule name
        String refUrl,           // link back to the triggering entity
        String actorLogin,       // user/bot that fired the trigger
        Instant occurredAt
) {
    public enum TriggerType {
        WEBHOOK,
        SCHEDULE,
        MANUAL,
        API_CALL
    }
}
