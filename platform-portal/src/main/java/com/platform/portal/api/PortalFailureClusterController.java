package com.platform.portal.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — failure clusters. Thin proxy to the ingestion service; the forwarded JWT carries the
 * user identity, so ingestion performs the VIEW/OPERATE_QUALITY capability checks.
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/failure-clusters")
@Tag(name = "Portal Failure Clusters")
public class PortalFailureClusterController {

  private final RestClient ingestionClient;

  public PortalFailureClusterController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  private String base(String projectId) {
    return "/api/v1/projects/" + projectId + "/failure-clusters";
  }

  @GetMapping
  public Object list(
      @PathVariable String projectId, @RequestParam(required = false) String status) {
    String uri = base(projectId) + (status != null ? "?status=" + status : "");
    return ingestionClient
        .get()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/{id}")
  public Object get(@PathVariable String projectId, @PathVariable String id) {
    return ingestionClient
        .get()
        .uri(base(projectId) + "/" + id)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PatchMapping("/{id}")
  public Object updateStatus(
      @PathVariable String projectId, @PathVariable String id, @RequestBody Object body) {
    return ingestionClient
        .patch()
        .uri(base(projectId) + "/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/{id}/link-defect")
  public Object linkDefect(
      @PathVariable String projectId, @PathVariable String id, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri(base(projectId) + "/" + id + "/link-defect")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/{id}/create-defect")
  public Object createDefect(
      @PathVariable String projectId, @PathVariable String id, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri(base(projectId) + "/" + id + "/create-defect")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/{id}/retest")
  public Object retest(@PathVariable String projectId, @PathVariable String id) {
    return ingestionClient
        .post()
        .uri(base(projectId) + "/" + id + "/retest")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }
}
