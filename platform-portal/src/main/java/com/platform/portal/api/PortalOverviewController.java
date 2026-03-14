package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Portal BFF — aggregates org-level overview data.
 *
 * <p>GET /api/portal/overview   — org summary + recent alerts
 * <p>GET /api/portal/teams      — list all teams
 * <p>GET /api/portal/projects   — list all projects (optionally by teamSlug)
 */
@RestController
@RequestMapping("/api/portal")
@Tag(name = "Portal Overview", description = "Organization-level overview data for the portal")
public class PortalOverviewController {

    private final RestClient ingestionClient;
    private final RestClient analyticsClient;

    public PortalOverviewController(
            @Qualifier("ingestionClient") RestClient ingestionClient,
            @Qualifier("analyticsClient") RestClient analyticsClient) {
        this.ingestionClient = ingestionClient;
        this.analyticsClient = analyticsClient;
    }

    @GetMapping("/overview")
    @Operation(summary = "Organization overview — summary stats + recent alerts")
    public Map<String, Object> overview(@RequestParam(defaultValue = "7") int days) {
        Object orgSummary = analyticsClient.get()
                .uri("/api/v1/analytics/org/summary?days=" + days)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);

        Object alerts = analyticsClient.get()
                .uri("/api/v1/alerts/org?days=" + days)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);

        return Map.of("summary", orgSummary != null ? orgSummary : Map.of(),
                      "recentAlerts", alerts != null ? alerts : java.util.List.of());
    }

    @GetMapping("/teams")
    @Operation(summary = "List all teams")
    public Object teams() {
        return ingestionClient.get()
                .uri("/api/v1/teams")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/projects")
    @Operation(summary = "List projects, optionally filtered by teamSlug")
    public Object projects(@RequestParam(required = false) String teamSlug) {
        String uri = teamSlug != null
                ? "/api/v1/projects?teamSlug=" + teamSlug
                : "/api/v1/projects";
        return ingestionClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }
}
