package com.platform.ingestion.audit;

import com.platform.core.domain.AuditEvent;

/** An audit event as shown in the project Audit Log. */
public record AuditEventDto(
    String id,
    String eventType,
    String actorUsername,
    String actorUserId,
    String actorKeyPrefix,
    String resourceType,
    String resourceId,
    String outcome,
    String details,
    String occurredAt) {

  public static AuditEventDto from(AuditEvent a) {
    return new AuditEventDto(
        a.getId() != null ? a.getId().toString() : null,
        a.getEventType(),
        a.getActorUsername(),
        a.getActorUserId() != null ? a.getActorUserId().toString() : null,
        a.getActorKeyPrefix(),
        a.getResourceType(),
        a.getResourceId(),
        a.getOutcome(),
        a.getDetails(),
        a.getOccurredAt() != null ? a.getOccurredAt().toString() : null);
  }
}
