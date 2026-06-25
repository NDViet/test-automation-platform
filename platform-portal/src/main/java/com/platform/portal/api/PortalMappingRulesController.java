package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Portal BFF for mapping-rule overrides (Mapping Suggester). Resolution: PROJECT → ORG → built-in
 * default; DELETE resets to the parent scope.
 */
@RestController
@RequestMapping("/api/portal")
public class PortalMappingRulesController {

  private final RestClient ingestionClient;

  public PortalMappingRulesController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping("/mapping-rules/default")
  public Object getDefault() {
    return ingestionClient
        .get()
        .uri("/api/v1/mapping-rules/default")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  // ── ORG ──────────────────────────────────────────────────────────────────────

  @GetMapping("/organizations/{orgId}/mapping-rules")
  public Object getOrg(@PathVariable String orgId) {
    return ingestionClient
        .get()
        .uri("/api/v1/organizations/" + orgId + "/mapping-rules")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/organizations/{orgId}/mapping-rules")
  public Object saveOrg(
      @PathVariable String orgId,
      @RequestBody Object body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return proxyPut("/api/v1/organizations/" + orgId + "/mapping-rules", body, actor);
  }

  @DeleteMapping("/organizations/{orgId}/mapping-rules")
  public ResponseEntity<Void> resetOrg(@PathVariable String orgId) {
    ingestionClient
        .delete()
        .uri("/api/v1/organizations/" + orgId + "/mapping-rules")
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  // ── PROJECT ───────────────────────────────────────────────────────────────────

  @GetMapping("/projects/{projectId}/mapping-rules")
  public Object getProject(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/mapping-rules")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/projects/{projectId}/mapping-rules")
  public Object saveProject(
      @PathVariable String projectId,
      @RequestBody Object body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return proxyPut("/api/v1/projects/" + projectId + "/mapping-rules", body, actor);
  }

  @DeleteMapping("/projects/{projectId}/mapping-rules")
  public ResponseEntity<Void> resetProject(@PathVariable String projectId) {
    ingestionClient
        .delete()
        .uri("/api/v1/projects/" + projectId + "/mapping-rules")
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  /**
   * Forwards a PUT and propagates the downstream status + body (so 400 validation errors reach the
   * client).
   */
  private Object proxyPut(String uri, Object body, String actor) {
    try {
      return ingestionClient
          .put()
          .uri(uri)
          .headers(
              h -> {
                if (actor != null) h.set("X-Actor", actor);
              })
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Object.class);
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString());
    }
  }
}
