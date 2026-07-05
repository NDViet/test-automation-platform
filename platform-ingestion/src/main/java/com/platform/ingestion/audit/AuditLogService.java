package com.platform.ingestion.audit;

import com.platform.core.repository.AuditEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project-scoped audit-log reads for the governance UI. */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

  private final AuditEventRepository repo;

  public AuditLogService(AuditEventRepository repo) {
    this.repo = repo;
  }

  public List<AuditEventDto> list(
      UUID projectId, String eventType, String resourceType, int days, int limit) {
    Instant since = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
    int capped = Math.min(Math.max(1, limit), 500);
    return repo
        .findForProject(
            projectId,
            blankToNull(eventType),
            blankToNull(resourceType),
            since,
            PageRequest.of(0, capped))
        .stream()
        .map(AuditEventDto::from)
        .toList();
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
