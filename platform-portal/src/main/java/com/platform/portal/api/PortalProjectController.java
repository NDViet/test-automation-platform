package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal BFF — project-level data (pass rate, flakiness, quality gate, executions).
 */
@RestController
@RequestMapping("/api/portal/projects")
@Tag(name = "Portal Projects", description = "Project-level data for the portal")
public class PortalProjectController {

    private final RestClient ingestionClient;
    private final RestClient analyticsClient;
    private final RestClient aiClient;

    public PortalProjectController(
            @Qualifier("ingestionClient") RestClient ingestionClient,
            @Qualifier("analyticsClient") RestClient analyticsClient,
            @Qualifier("aiClient")        RestClient aiClient) {
        this.ingestionClient = ingestionClient;
        this.analyticsClient = analyticsClient;
        this.aiClient        = aiClient;
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Project detail — flakiness, quality gate, and recent executions")
    public Map<String, Object> projectDetail(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "7") int days) {

        Object project = ingestionClient.get()
                .uri("/api/v1/projects/" + projectId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);

        Object flakiness = safeGet(analyticsClient,
                "/api/v1/analytics/" + projectId + "/flakiness?limit=10");

        Object gate = safeGet(analyticsClient,
                "/api/v1/analytics/" + projectId + "/quality-gate");

        Object passRateTrend = safeGet(analyticsClient,
                "/api/v1/analytics/" + projectId + "/trends/pass-rate?days=" + days);

        Object executions = safeGet(ingestionClient,
                "/api/v1/projects/" + projectId + "/executions?limit=10");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("flakiness", flakiness);
        result.put("qualityGate", gate);
        result.put("passRateTrend", passRateTrend);
        result.put("recentExecutions", executions);
        return result;
    }

    @GetMapping("/{projectId}/executions")
    @Operation(summary = "List executions for a project")
    public Object executions(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "20") int limit) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/executions?limit=" + limit)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @GetMapping("/{projectId}/trends/pass-rate")
    @Operation(summary = "Pass rate trend for a project")
    public Object passRateTrend(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "30") int days) {
        return analyticsClient.get()
                .uri("/api/v1/analytics/" + projectId + "/trends/pass-rate?days=" + days)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @GetMapping("/{projectId}/flakiness")
    @Operation(summary = "Flaky tests for a project")
    public Object flakiness(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String classification) {
        String uri = "/api/v1/analytics/" + projectId + "/flakiness?limit=" + limit;
        if (classification != null) uri += "&classification=" + classification;
        return analyticsClient.get().uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @GetMapping("/{projectId}/quality-gate")
    @Operation(summary = "Quality gate evaluation for a project")
    public Object qualityGate(@PathVariable String projectId) {
        return analyticsClient.get()
                .uri("/api/v1/analytics/" + projectId + "/quality-gate")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @GetMapping("/{projectId}/analyses")
    @Operation(summary = "AI failure analyses for a project")
    public Object analyses(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "7") int days) {
        return aiClient.get()
                .uri("/api/v1/projects/" + projectId + "/analyses?days=" + days)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @GetMapping("/{projectId}/impact")
    @Operation(summary = "Test Impact Analysis — recommended tests for changed files")
    public Object testImpact(
            @PathVariable String projectId,
            @RequestParam(required = false) List<String> changedFiles,
            @RequestParam(required = false) List<String> changedClasses) {
        StringBuilder uri = new StringBuilder("/api/v1/analytics/")
                .append(projectId).append("/impact");
        String sep = "?";
        if (changedFiles != null) {
            for (String f : changedFiles) {
                uri.append(sep).append("changedFiles=").append(f);
                sep = "&";
            }
        }
        if (changedClasses != null) {
            for (String c : changedClasses) {
                uri.append(sep).append("changedClasses=").append(c);
                sep = "&";
            }
        }
        return safeGet(analyticsClient, uri.toString());
    }

    @GetMapping("/{projectId}/impact/summary")
    @Operation(summary = "Coverage mapping summary — how many tests are mapped for TIA")
    public Object impactSummary(@PathVariable String projectId) {
        return safeGet(analyticsClient, "/api/v1/analytics/" + projectId + "/impact/summary");
    }

    @GetMapping("/{projectId}/release-report")
    @Operation(summary = "Release quality report for a project")
    public Object releaseReport(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) String tag) {
        String uri = "/api/v1/projects/" + projectId + "/release-report?days=" + days;
        if (tag != null) uri += "&tag=" + tag;
        return analyticsClient.get().uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Object safeGet(RestClient client, String uri) {
        try {
            return client.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
