package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Portal BFF — Work-In-Progress / Review Queue.
 *
 * GET  /api/portal/projects/{projectId}/work-items                                          → GET  /hub/wip/{projectId}
 * POST /api/portal/projects/{projectId}/work-items/review-requests/{requestId}/approve      → POST /hub/wip/review-requests/{requestId}/approve
 * POST /api/portal/projects/{projectId}/work-items/review-requests/{requestId}/reject       → POST /hub/wip/review-requests/{requestId}/reject
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/work-items")
public class PortalWorkItemController {

    private final RestClient agentClient;

    public PortalWorkItemController(@Qualifier("agentClient") RestClient agentClient) {
        this.agentClient = agentClient;
    }

    @GetMapping("")
    public Object list(@PathVariable String projectId) {
        try {
            return agentClient.get()
                    .uri("/hub/wip/" + projectId)
                    .retrieve()
                    .body(List.class);
        } catch (RestClientException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/review-requests/{requestId}/approve")
    public Object approve(
            @PathVariable String projectId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body) {
        try {
            return agentClient.post()
                    .uri("/hub/wip/review-requests/" + requestId + "/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/review-requests/{requestId}/reject")
    public Object reject(
            @PathVariable String projectId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body) {
        try {
            return agentClient.post()
                    .uri("/hub/wip/review-requests/" + requestId + "/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            return Map.of("error", e.getMessage());
        }
    }
}
