package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

    // Append a raw query value; RestClient's URI template handling encodes it exactly once.
    // (UriComponentsBuilder + RestClient double-encodes backslash/space area paths.)
    private static String appendParam(StringBuilder uri, String sep, String key, String value) {
        if (value == null || value.isBlank()) return sep;
        uri.append(sep).append(key).append('=').append(value);
        return "&";
    }

    // ── Requirements ──────────────────────────────────────────────────────────

    @GetMapping("/requirements")
    public Object listRequirements(
            @PathVariable String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String iteration) {

        StringBuilder uri = new StringBuilder("/api/v1/projects/" + projectId + "/requirements");
        String sep = "?";
        sep = appendParam(uri, sep, "status", status);
        sep = appendParam(uri, sep, "issueType", issueType);
        sep = appendParam(uri, sep, "search", search);
        sep = appendParam(uri, sep, "area", area);
        sep = appendParam(uri, sep, "team", team);
        appendParam(uri, sep, "iteration", iteration);

        return ingestionClient.get()
                .uri(uri.toString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/requirements/page")
    public Object pageRequirements(
            @PathVariable String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String iteration,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        StringBuilder uri = new StringBuilder("/api/v1/projects/" + projectId + "/requirements/page")
                .append("?page=").append(page).append("&size=").append(size);
        appendParam(uri, "&", "status", status);
        appendParam(uri, "&", "issueType", issueType);
        appendParam(uri, "&", "search", search);
        appendParam(uri, "&", "area", area);
        appendParam(uri, "&", "team", team);
        appendParam(uri, "&", "iteration", iteration);

        return ingestionClient.get()
                .uri(uri.toString())
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

    @GetMapping("/requirements/{reqId}/relations")
    public Object getRequirementRelations(@PathVariable String projectId, @PathVariable String reqId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/requirements/" + reqId + "/relations")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    // ── Quality dashboards ──────────────────────────────────────────────────────

    @GetMapping("/quality/{view}")
    public Object quality(@PathVariable String projectId, @PathVariable String view) {
        // view = overview | engineers
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/quality/" + view)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/quality/activity")
    public Object qualityActivity(@PathVariable String projectId,
                                  @RequestParam String person,
                                  @RequestParam(defaultValue = "50") int limit) {
        return ingestionClient.get()
                .uri(b -> b.path("/api/v1/projects/" + projectId + "/quality/activity")
                        .queryParam("person", person).queryParam("limit", limit).build())
                .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
    }

    @GetMapping("/quality/involvement-items")
    public Object qualityInvolvementItems(@PathVariable String projectId,
                                          @RequestParam String person,
                                          @RequestParam String kind) {
        return ingestionClient.get()
                .uri(b -> b.path("/api/v1/projects/" + projectId + "/quality/involvement-items")
                        .queryParam("person", person).queryParam("kind", kind).build())
                .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
    }

    @PostMapping("/quality/sync-history")
    public Object syncHistory(@PathVariable String projectId) {
        try {
            return agentClient.post()
                    .uri("/api/agent/projects/" + projectId + "/quality/sync-history")
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
        } catch (RestClientException e) {
            return Map.of("success", false, "error", "Agent service unavailable — " + e.getMessage());
        }
    }

    @GetMapping("/quality/history-status")
    public Object historyStatus(@PathVariable String projectId) {
        try {
            return agentClient.get()
                    .uri("/api/agent/projects/" + projectId + "/quality/history-status")
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
        } catch (RestClientException e) {
            return Map.of("running", false);
        }
    }

    @GetMapping("/quality/work-items")
    public Object qualityWorkItems(@PathVariable String projectId,
                                   @RequestParam String person,
                                   @RequestParam(defaultValue = "assignee") String attribution,
                                   @RequestParam(defaultValue = "any") String type,
                                   @RequestParam(defaultValue = "any") String status) {
        return ingestionClient.get()
                .uri(b -> b.path("/api/v1/projects/" + projectId + "/quality/work-items")
                        .queryParam("person", person)
                        .queryParam("attribution", attribution)
                        .queryParam("type", type)
                        .queryParam("status", status)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    // ── Productivity (cycle time) ───────────────────────────────────────────────

    @GetMapping("/productivity/by-area")
    public Object productivityByArea(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/productivity/by-area")
                .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
    }

    @GetMapping("/productivity/wip-items")
    public Object productivityWipItems(@PathVariable String projectId,
                                       @RequestParam(required = false) String area,
                                       @RequestParam(defaultValue = "true") boolean over) {
        return ingestionClient.get()
                .uri(b -> { var u = b.path("/api/v1/projects/" + projectId + "/productivity/wip-items").queryParam("over", over);
                            if (area != null) u.queryParam("area", area); return u.build(); })
                .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
    }

    @GetMapping("/productivity/lead-by-area")
    public Object productivityLeadByArea(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/productivity/lead-by-area")
                .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
    }

    @GetMapping("/productivity/lead-items")
    public Object productivityLeadItems(@PathVariable String projectId,
                                        @RequestParam(required = false) String area) {
        return ingestionClient.get()
                .uri(b -> { var u = b.path("/api/v1/projects/" + projectId + "/productivity/lead-items");
                            if (area != null) u.queryParam("area", area); return u.build(); })
                .accept(MediaType.APPLICATION_JSON).retrieve().body(Object.class);
    }

    @PutMapping("/productivity/threshold")
    public Object setProductivityThreshold(@PathVariable String projectId, @RequestBody Map<String, Object> body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/productivity/threshold")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(Object.class);
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
