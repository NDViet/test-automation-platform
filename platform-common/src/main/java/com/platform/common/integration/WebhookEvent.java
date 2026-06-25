package com.platform.common.integration;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Parsed, normalised webhook event after adapter verification and extraction. The raw payload is
 * available in {@code rawPayload} for adapter-specific processing.
 */
public record WebhookEvent(
    UUID integrationConfigId,
    IntegrationType source,
    EventAction action,
    String entityType, // "issue", "pull_request", "pipeline_run", etc.
    String entityExternalId,
    Map<String, Object> rawPayload,
    Instant receivedAt) {
  public enum EventAction {
    CREATED,
    UPDATED,
    DELETED,
    TRANSITIONED,
    COMPLETED,
    FAILED
  }
}
