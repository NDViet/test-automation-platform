package com.platform.portal.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** Portal BFF — project audit log. Thin proxy to ingestion (enforces VIEW_RESULTS). */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/audit-log")
@Tag(name = "Portal Audit Log")
public class PortalAuditLogController {

  private final RestClient ingestionClient;

  public PortalAuditLogController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping
  public Object list(
      @PathVariable String projectId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String resourceType,
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "200") int limit) {
    StringBuilder uri =
        new StringBuilder(
            "/api/v1/projects/" + projectId + "/audit-log?days=" + days + "&limit=" + limit);
    if (eventType != null && !eventType.isBlank()) uri.append("&eventType=").append(eventType);
    if (resourceType != null && !resourceType.isBlank())
      uri.append("&resourceType=").append(resourceType);
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }
}
