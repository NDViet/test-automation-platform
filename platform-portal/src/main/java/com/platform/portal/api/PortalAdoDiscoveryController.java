package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * BFF proxy for Azure DevOps schema discovery + mapping suggestions. Proxies to
 * platform-ingestion's {@code /api/v1/projects/{id}/ado/**}.
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/ado")
public class PortalAdoDiscoveryController {

  private final RestClient ingestionClient;

  public PortalAdoDiscoveryController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping("/projects")
  public Object projects(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/{p}/ado/projects", projectId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/work-item-types")
  public Object types(@PathVariable String projectId, @RequestParam String adoProject) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/{p}/ado/work-item-types?adoProject={a}", projectId, adoProject)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/work-item-types/{type}/schema")
  public Object schema(
      @PathVariable String projectId, @PathVariable String type, @RequestParam String adoProject) {
    return ingestionClient
        .get()
        .uri(
            "/api/v1/projects/{p}/ado/work-item-types/{t}/schema?adoProject={a}",
            projectId,
            type,
            adoProject)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/work-item-types/{type}/drift")
  public Object drift(
      @PathVariable String projectId,
      @PathVariable String type,
      @RequestParam String adoProject,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return ingestionClient
        .get()
        .uri(
            "/api/v1/projects/{p}/ado/work-item-types/{t}/drift?adoProject={a}",
            projectId,
            type,
            adoProject)
        .headers(
            h -> {
              if (actor != null) h.set("X-Actor", actor);
            })
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/work-item-types/{type}/drift/baseline")
  public Object captureBaseline(
      @PathVariable String projectId,
      @PathVariable String type,
      @RequestParam String adoProject,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return ingestionClient
        .post()
        .uri(
            "/api/v1/projects/{p}/ado/work-item-types/{t}/drift/baseline?adoProject={a}",
            projectId,
            type,
            adoProject)
        .headers(
            h -> {
              if (actor != null) h.set("X-Actor", actor);
            })
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }
}
