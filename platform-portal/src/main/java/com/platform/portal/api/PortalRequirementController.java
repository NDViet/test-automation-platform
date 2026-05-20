package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/portal/projects/{projectId}")
public class PortalRequirementController {

    private final RestClient ingestionClient;
    private final RestClient agentClient;

    public PortalRequirementController(
            @Qualifier("ingestionClient") RestClient ingestionClient,
            @Qualifier("agentClient")    RestClient agentClient) {
        this.ingestionClient = ingestionClient;
        this.agentClient     = agentClient;
    }

    // ── Requirements ──────────────────────────────────────────────────────────

    @GetMapping("/requirements")
    public Object listRequirements(
            @PathVariable String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String search) {

        UriComponentsBuilder uri = UriComponentsBuilder
                .fromPath("/api/v1/projects/" + projectId + "/requirements");
        if (status    != null) uri.queryParam("status",    status);
        if (issueType != null) uri.queryParam("issueType", issueType);
        if (search    != null) uri.queryParam("search",    search);

        return ingestionClient.get()
                .uri(uri.toUriString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/requirements/stats")
    public Object requirementStats(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/requirements/stats")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/requirements/{reqId}")
    public Object getRequirement(@PathVariable String projectId, @PathVariable String reqId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/requirements/" + reqId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    // ── PR Analyses ───────────────────────────────────────────────────────────

    @GetMapping("/pr-analyses")
    public Object prAnalyses(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "30") int limit) {
        try {
            return agentClient.get()
                    .uri("/hub/workflows/pr-analyses?projectId=" + projectId + "&limit=" + limit)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            return Map.of("error", "Agent service unavailable — " + e.getMessage());
        }
    }
}
