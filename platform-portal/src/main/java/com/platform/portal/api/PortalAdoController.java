package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** BFF for the synced ADO org structure: reads via ingestion, sync trigger via agent. */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/ado")
public class PortalAdoController {

    private final RestClient ingestionClient;
    private final RestClient agentClient;

    public PortalAdoController(@Qualifier("ingestionClient") RestClient ingestionClient,
                              @Qualifier("agentClient") RestClient agentClient) {
        this.ingestionClient = ingestionClient;
        this.agentClient     = agentClient;
    }

    @GetMapping("/{kind}")
    public Object list(@PathVariable String projectId, @PathVariable String kind) {
        // kind = teams | areas | iterations | users | summary
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/ado/" + kind)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @PutMapping("/users/{userId}/quality-role")
    public Object setQualityRole(@PathVariable String projectId, @PathVariable String userId,
                                 @RequestBody Map<String, Object> body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/ado/users/" + userId + "/quality-role")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
    }

    @PostMapping("/sync-structure")
    public Object syncStructure(@PathVariable String projectId) {
        try {
            return agentClient.post()
                    .uri("/api/agent/projects/" + projectId + "/ado/sync-structure")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            return Map.of("success", false, "error", "Agent service unavailable — " + e.getMessage());
        }
    }
}
