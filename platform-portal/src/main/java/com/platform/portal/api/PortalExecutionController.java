package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — execution (run) detail.
 */
@RestController
@RequestMapping("/api/portal/executions")
@Tag(name = "Portal Executions", description = "Test run detail for the portal")
public class PortalExecutionController {

    private final RestClient ingestionClient;

    public PortalExecutionController(@Qualifier("ingestionClient") RestClient ingestionClient) {
        this.ingestionClient = ingestionClient;
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get full run detail including all test case results")
    public Object runDetail(@PathVariable String runId) {
        return ingestionClient.get()
                .uri("/api/v1/executions/" + runId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }
}
