package com.platform.common.model;

import com.platform.common.integration.IntegrationType;
import java.time.Instant;
import java.util.UUID;

/**
 * Platform-canonical production monitor (alert, SLO, health check). Sourced from Tier 5 integration
 * adapters (Datadog, PagerDuty, etc.).
 */
public record MonitorRecord(
    UUID id,
    String externalId,
    IntegrationType source,
    String name,
    MonitorType type,
    MonitorStatus status,
    String serviceTag,
    String severity,
    Instant lastTriggeredAt) {
  public enum MonitorType {
    ALERT,
    SLO,
    HEALTH_CHECK,
    INCIDENT
  }

  public enum MonitorStatus {
    OK,
    ALERTING,
    INCIDENT,
    RESOLVED,
    NO_DATA
  }
}
