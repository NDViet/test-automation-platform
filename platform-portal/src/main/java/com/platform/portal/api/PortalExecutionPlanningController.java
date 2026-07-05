package com.platform.portal.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — execution planning (My Queue, assignable users, assign). Thin proxy to ingestion.
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}")
@Tag(name = "Portal Execution Planning")
public class PortalExecutionPlanningController {

  private final RestClient ingestionClient;

  public PortalExecutionPlanningController(
      @Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping("/my-queue")
  public Object myQueue(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/my-queue")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/assignable-users")
  public Object assignableUsers(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/assignable-users")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-runs/{runId}/executions/{execId}/assignee")
  public Object assign(
      @PathVariable String projectId,
      @PathVariable String runId,
      @PathVariable String execId,
      @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-runs/"
                + runId
                + "/executions/"
                + execId
                + "/assignee")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }
}
