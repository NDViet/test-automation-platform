package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
@RequestMapping("/api/portal")
public class PortalProjectManagementController {

    private final RestClient ingestionClient;
    private final RestClient agentClient;

    public PortalProjectManagementController(
            @Qualifier("ingestionClient") RestClient ingestionClient,
            @Qualifier("agentClient")    RestClient agentClient) {
        this.ingestionClient = ingestionClient;
        this.agentClient     = agentClient;
    }

    // ── Organizations (top tenant — ADO-first) ──────────────────────────────────

    @PostMapping("/organizations")
    public ResponseEntity<Object> createOrganization(@RequestBody Object body) {
        Object result = ingestionClient.post()
                .uri("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
        return ResponseEntity.status(201).body(result);
    }

    @PutMapping("/organizations/{id}")
    public Object updateOrganization(@PathVariable String id, @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/organizations/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
    }

    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable String id) {
        ingestionClient.delete()
                .uri("/api/v1/organizations/" + id)
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    // ── Teams (sub-entities of a project — ADO-first) ───────────────────────────

    @GetMapping("/projects/{projectId}/teams")
    public Object listTeams(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/teams")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @PostMapping("/projects/{projectId}/teams")
    public ResponseEntity<Object> createTeam(@PathVariable String projectId, @RequestBody Object body) {
        Object result = ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
        return ResponseEntity.status(201).body(result);
    }

    @PutMapping("/projects/{projectId}/teams/{id}")
    public Object updateTeam(@PathVariable String projectId, @PathVariable String id, @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/teams/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
    }

    @DeleteMapping("/projects/{projectId}/teams/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable String projectId, @PathVariable String id) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + projectId + "/teams/" + id)
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    @PostMapping("/projects")
    public Object createProject(@RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
    }

    @PutMapping("/projects/{id}")
    public Object updateProject(@PathVariable String id, @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + id)
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{id}/integrations")
    public Object listIntegrations(@PathVariable String id) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + id + "/integrations")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/projects/{id}/integrations/inherited")
    public Object listInheritedIntegrations(@PathVariable String id) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + id + "/integrations/inherited")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Object.class);
    }

    @PostMapping("/projects/{id}/integrations")
    public Object saveIntegration(@PathVariable String id, @RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + id + "/integrations")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Object.class);
    }

    @DeleteMapping("/projects/{id}/integrations/{configId}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable String id, @PathVariable String configId) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + id + "/integrations/" + configId)
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{id}/integrations/sync")
    public Object syncIntegrations(@PathVariable String id) {
        try {
            return agentClient.post()
                    .uri("/api/agent/projects/" + id + "/integrations/sync")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            return Map.of("error", "Agent service unavailable — " + e.getMessage());
        }
    }
}
