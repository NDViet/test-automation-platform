package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Portal BFF — AI settings and on-demand analysis.
 */
@RestController
@RequestMapping("/api/portal/ai")
@Tag(name = "Portal AI", description = "AI settings and on-demand analysis for the portal")
public class PortalAiController {

    private final RestClient aiClient;

    public PortalAiController(@Qualifier("aiClient") RestClient aiClient) {
        this.aiClient = aiClient;
    }

    @GetMapping("/settings")
    @Operation(summary = "Get AI provider settings")
    public Object getSettings() {
        return aiClient.get()
                .uri("/api/v1/ai/settings")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PutMapping("/settings")
    @Operation(summary = "Update AI provider settings")
    public Object updateSettings(@RequestBody Map<String, Object> body) {
        return aiClient.put()
                .uri("/api/v1/ai/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @PostMapping("/settings/test")
    @Operation(summary = "Test AI provider connectivity")
    public Object testConnection(@RequestBody Map<String, Object> body) {
        return aiClient.post()
                .uri("/api/v1/ai/settings/test")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @PostMapping("/projects/{projectId}/results/{resultId}/analyse")
    @Operation(summary = "On-demand AI analysis for a test result")
    public Object analyseResult(
            @PathVariable String projectId,
            @PathVariable String resultId) {
        return aiClient.post()
                .uri("/api/v1/projects/" + projectId + "/results/" + resultId + "/analyse")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/analyse/run-now")
    @Operation(summary = "Trigger on-demand batch analysis of all unanalysed failures")
    public Object runNow(@RequestParam(defaultValue = "24") int hours) {
        return aiClient.post()
                .uri("/api/v1/analyse/run-now?hours=" + hours)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }
}
