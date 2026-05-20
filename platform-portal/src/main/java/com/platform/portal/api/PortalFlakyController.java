package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Portal BFF — flaky test management.
 * POST /api/portal/projects/{projectId}/flakiness/recompute  — trigger score recomputation
 * POST /api/portal/projects/{projectId}/flakiness/fix        — trigger AI fix (→ HealingNode)
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/flakiness")
public class PortalFlakyController {

    private final RestClient analyticsClient;
    private final RestClient agentClient;

    public PortalFlakyController(
            @Qualifier("analyticsClient") RestClient analyticsClient,
            @Qualifier("agentClient")    RestClient agentClient) {
        this.analyticsClient = analyticsClient;
        this.agentClient     = agentClient;
    }

    @PostMapping("/recompute")
    public Object recompute(@PathVariable String projectId) {
        return analyticsClient.post()
                .uri("/api/v1/analytics/" + projectId + "/flakiness/recompute")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/fix")
    public Object triggerFix(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> body) {
        return agentClient.post()
                .uri("/hub/healing/" + projectId + "/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }
}
