package com.platform.ingestion.audit;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Project-scoped audit log (read-only governance view). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/audit-log")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
@Tag(name = "Audit Log")
public class AuditLogController {

  private final AuditLogService service;

  public AuditLogController(AuditLogService service) {
    this.service = service;
  }

  @GetMapping
  public List<AuditEventDto> list(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String resourceType,
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "200") int limit) {
    return service.list(projectId, eventType, resourceType, days, limit);
  }
}
