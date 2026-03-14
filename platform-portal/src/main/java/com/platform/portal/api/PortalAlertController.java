package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — alert history.
 */
@RestController
@RequestMapping("/api/portal/alerts")
@Tag(name = "Portal Alerts", description = "Alert history for the portal")
public class PortalAlertController {

    private final RestClient analyticsClient;

    public PortalAlertController(@Qualifier("analyticsClient") RestClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    @GetMapping
    @Operation(summary = "List org-wide alerts")
    public Object orgAlerts(@RequestParam(defaultValue = "7") int days) {
        return analyticsClient.get()
                .uri("/api/v1/alerts/org?days=" + days)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @GetMapping("/projects/{projectId}")
    @Operation(summary = "List alerts for a project")
    public Object projectAlerts(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "7") int days) {
        return analyticsClient.get()
                .uri("/api/v1/alerts/projects/" + projectId + "?days=" + days)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }
}
