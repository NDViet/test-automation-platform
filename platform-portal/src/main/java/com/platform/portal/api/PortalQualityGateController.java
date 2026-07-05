package com.platform.portal.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — release quality gate. Thin proxy to ingestion; the forwarded JWT carries the user,
 * so ingestion enforces VIEW / MANAGE_PROJECT / OPERATE_QUALITY.
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}")
@Tag(name = "Portal Quality Gate")
public class PortalQualityGateController {

  private final RestClient ingestionClient;

  public PortalQualityGateController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping("/gate-config")
  public Object getConfig(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/quality-gate")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/gate-config")
  public Object updateConfig(@PathVariable String projectId, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/quality-gate")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/releases/{releaseId}/evaluate-gate")
  public Object evaluate(@PathVariable String projectId, @PathVariable String releaseId) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/releases/" + releaseId + "/evaluate-gate")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/releases/{releaseId}/override-gate")
  public Object override(
      @PathVariable String projectId, @PathVariable String releaseId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/releases/" + releaseId + "/override-gate")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }
}
