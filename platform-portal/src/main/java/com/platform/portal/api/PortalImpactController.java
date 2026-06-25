package com.platform.portal.api;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Portal BFF — Impact Analysis management.
 *
 * <p>GET /api/portal/projects/{projectId}/impact-analyses → GET /hub/impact/{projectId} POST
 * /api/portal/projects/{projectId}/impact-analyses → POST /hub/impact/{projectId} GET
 * /api/portal/projects/{projectId}/impact-analyses/prs → GET /hub/impact/{projectId}/prs GET
 * /api/portal/projects/{projectId}/impact-analyses/{id} → GET /hub/impact/{projectId}/{id}
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/impact-analyses")
public class PortalImpactController {

  private final RestClient agentClient;

  public PortalImpactController(@Qualifier("agentClient") RestClient agentClient) {
    this.agentClient = agentClient;
  }

  @GetMapping("")
  public Object list(@PathVariable String projectId) {
    try {
      return agentClient.get().uri("/hub/impact/" + projectId).retrieve().body(List.class);
    } catch (RestClientException e) {
      return Map.of("error", e.getMessage());
    }
  }

  @PostMapping("")
  public Object create(@PathVariable String projectId, @RequestBody Map<String, Object> body) {
    try {
      return agentClient
          .post()
          .uri("/hub/impact/" + projectId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Object.class);
    } catch (RestClientException e) {
      return Map.of("error", e.getMessage());
    }
  }

  @GetMapping("/prs")
  public Object listPrs(@PathVariable String projectId) {
    try {
      return agentClient.get().uri("/hub/impact/" + projectId + "/prs").retrieve().body(List.class);
    } catch (RestClientException e) {
      return Map.of("error", e.getMessage());
    }
  }

  @GetMapping("/{id}")
  public Object get(@PathVariable String projectId, @PathVariable String id) {
    try {
      return agentClient
          .get()
          .uri("/hub/impact/" + projectId + "/" + id)
          .retrieve()
          .body(Object.class);
    } catch (RestClientException e) {
      return Map.of("error", e.getMessage());
    }
  }
}
