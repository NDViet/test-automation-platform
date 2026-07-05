package com.platform.ingestion.audit;

import com.platform.core.domain.AuditEvent;
import com.platform.core.repository.AuditEventRepository;
import com.platform.security.jwt.AuthenticatedUser;
import com.platform.security.web.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes an audit event for a portal-user action directly (as opposed to the {@link AuditLog}
 * aspect, which only records event/resource type). Use where the event needs the concrete project,
 * resource id, and the signed-in actor — e.g. test-case approve/reject/delete/edit.
 */
@Component
public class AuditWriter {

  private final AuditEventRepository repo;

  public AuditWriter(AuditEventRepository repo) {
    this.repo = repo;
  }

  public void record(
      String eventType, UUID projectId, String resourceType, String resourceId, String details) {
    AuthenticatedUser user = CurrentUser.get();
    repo.save(
        AuditEvent.builder()
            .eventType(eventType)
            .projectId(projectId)
            .actorUserId(user != null ? user.userId() : null)
            .actorUsername(user != null ? user.username() : null)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .details(details)
            .outcome("SUCCESS")
            .build());
  }
}
