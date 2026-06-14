package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * BFF proxy for scoped integration credentials (the centralized Admin PAT +
 * Team/Project overrides). Proxies to platform-ingestion's
 * {@code /api/v1/credentials} API.
 */
@RestController
@RequestMapping("/api/portal/credentials")
public class PortalCredentialController {

    private final RestClient ingestionClient;

    public PortalCredentialController(@Qualifier("ingestionClient") RestClient ingestionClient) {
        this.ingestionClient = ingestionClient;
    }

    @GetMapping
    public Object list(@RequestParam String scope,
                       @RequestParam(required = false) String scopeId) {
        StringBuilder uri = new StringBuilder("/api/v1/credentials?scope=").append(scope);
        if (scopeId != null && !scopeId.isBlank()) uri.append("&scopeId=").append(scopeId);
        return ingestionClient.get()
                .uri(uri.toString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @PostMapping
    public Object save(@RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        ingestionClient.delete()
                .uri("/api/v1/credentials/" + id)
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public Object test(@PathVariable String id) {
        return ingestionClient.post()
                .uri("/api/v1/credentials/" + id + "/test")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }
}
