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

/** Portal BFF — shared-step library. Thin proxy to ingestion. */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/shared-steps")
@Tag(name = "Portal Shared Steps")
public class PortalSharedStepController {

  private final RestClient ingestionClient;

  public PortalSharedStepController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  private String base(String projectId) {
    return "/api/v1/projects/" + projectId + "/shared-steps";
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
}
