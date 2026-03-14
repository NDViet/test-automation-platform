package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — API key management (proxies to platform-ingestion).
 */
@RestController
@RequestMapping("/api/portal/api-keys")
@Tag(name = "Portal API Keys", description = "API key management for the portal")
public class PortalApiKeyController {

    private final RestClient ingestionClient;

    public PortalApiKeyController(@Qualifier("ingestionClient") RestClient ingestionClient) {
        this.ingestionClient = ingestionClient;
    }

    @GetMapping
    @Operation(summary = "List API keys for a team")
    public Object list(@RequestParam String teamId) {
        return ingestionClient.get()
                .uri("/api/v1/api-keys?teamId=" + teamId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping
    @Operation(summary = "Create an API key")
    public Object create(@RequestBody Object request) {
        return ingestionClient.post()
                .uri("/api/v1/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve().body(Object.class);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key")
    public ResponseEntity<Void> revoke(@PathVariable String id) {
        ingestionClient.delete()
                .uri("/api/v1/api-keys/" + id)
                .retrieve().toBodilessEntity();
        return ResponseEntity.noContent().build();
    }
}
