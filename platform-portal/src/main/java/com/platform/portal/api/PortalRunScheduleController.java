package com.platform.portal.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** Portal BFF — CI run schedules. Thin proxy to ingestion (which enforces MANAGE_PROJECT). */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/run-schedules")
@Tag(name = "Portal Run Schedules")
public class PortalRunScheduleController {

  private final RestClient ingestionClient;

  public PortalRunScheduleController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  private String base(String projectId) {
    return "/api/v1/projects/" + projectId + "/run-schedules";
  }

  @GetMapping
  public Object list(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri(base(projectId))
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping
  public Object create(@PathVariable String projectId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri(base(projectId))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/{id}")
  public Object update(
      @PathVariable String projectId, @PathVariable String id, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri(base(projectId) + "/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String projectId, @PathVariable String id) {
    ingestionClient.delete().uri(base(projectId) + "/" + id).retrieve().toBodilessEntity();
  }

  @PostMapping("/{id}/dispatch")
  public Object dispatch(@PathVariable String projectId, @PathVariable String id) {
    return ingestionClient
        .post()
        .uri(base(projectId) + "/" + id + "/dispatch")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }
}
