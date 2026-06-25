package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * BFF proxy for RBAC role administration. Forwards the {@code X-Actor} header (the acting user) so
 * platform-ingestion can enforce authorization.
 */
@RestController
@RequestMapping("/api/portal/rbac/members")
public class PortalRoleController {

  private final RestClient ingestionClient;

  public PortalRoleController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping
  public Object list(@RequestParam String scope, @RequestParam(required = false) String scopeId) {
    StringBuilder uri = new StringBuilder("/api/v1/rbac/members?scope=").append(scope);
    if (scopeId != null && !scopeId.isBlank()) uri.append("&scopeId=").append(scopeId);
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping
  public Object grant(
      @RequestHeader(value = "X-Actor", required = false) String actor, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/rbac/members")
        .header("X-Actor", actor != null ? actor : "")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(
      @RequestHeader(value = "X-Actor", required = false) String actor, @PathVariable String id) {
    ingestionClient
        .delete()
        .uri("/api/v1/rbac/members/" + id)
        .header("X-Actor", actor != null ? actor : "")
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }
}
