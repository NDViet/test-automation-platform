package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — Test Case Management (TCM): suites, managed test cases, test runs, executions.
 * All endpoints proxy to platform-ingestion (/api/v1/) or platform-agent (/hub/).
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}")
@Tag(name = "Portal TCM", description = "Test Case Management endpoints for the portal")
public class PortalTestCaseController {

    private final RestClient ingestionClient;
    private final RestClient agentClient;

    public PortalTestCaseController(
            @Qualifier("ingestionClient") RestClient ingestionClient,
            @Qualifier("agentClient")    RestClient agentClient) {
        this.ingestionClient = ingestionClient;
        this.agentClient     = agentClient;
    }

    // ── Test Suites ────────────────────────────────────────────────────────────

    @GetMapping("/test-suites")
    @Operation(summary = "List test suites for a project")
    public Object listTestSuites(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/test-suites")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-suites")
    @Operation(summary = "Create a test suite")
    public Object createTestSuite(@PathVariable String projectId,
                                  @RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-suites")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @PutMapping("/test-suites/{suiteId}")
    @Operation(summary = "Update a test suite")
    public Object updateTestSuite(@PathVariable String projectId,
                                  @PathVariable String suiteId,
                                  @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/test-suites/" + suiteId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @DeleteMapping("/test-suites/{suiteId}")
    @Operation(summary = "Delete a test suite")
    public ResponseEntity<Void> deleteTestSuite(@PathVariable String projectId,
                                                @PathVariable String suiteId) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + projectId + "/test-suites/" + suiteId)
                .retrieve().toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    // ── Test Cases ─────────────────────────────────────────────────────────────

    @GetMapping("/test-cases")
    @Operation(summary = "List managed test cases for a project")
    public Object listTestCases(@PathVariable String projectId,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String suiteId,
                                @RequestParam(required = false) String search) {
        StringBuilder uri = new StringBuilder("/api/v1/projects/").append(projectId).append("/test-cases");
        String sep = "?";
        if (status != null)  { uri.append(sep).append("status=").append(status);    sep = "&"; }
        if (suiteId != null) { uri.append(sep).append("suiteId=").append(suiteId);  sep = "&"; }
        if (search != null)  { uri.append(sep).append("search=").append(search); }
        return ingestionClient.get()
                .uri(uri.toString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases")
    @Operation(summary = "Create a managed test case")
    public Object createTestCase(@PathVariable String projectId,
                                 @RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-cases")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @GetMapping("/test-cases/{tcId}")
    @Operation(summary = "Get a managed test case by ID")
    public Object getTestCase(@PathVariable String projectId,
                              @PathVariable String tcId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PutMapping("/test-cases/{tcId}")
    @Operation(summary = "Update a managed test case")
    public Object updateTestCase(@PathVariable String projectId,
                                 @PathVariable String tcId,
                                 @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @DeleteMapping("/test-cases/{tcId}")
    @Operation(summary = "Delete a managed test case")
    public ResponseEntity<Void> deleteTestCase(@PathVariable String projectId,
                                               @PathVariable String tcId) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId)
                .retrieve().toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/test-cases/{tcId}/steps")
    @Operation(summary = "Replace steps of a test case")
    public Object replaceSteps(@PathVariable String projectId,
                               @PathVariable String tcId,
                               @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/steps")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/{tcId}/submit-review")
    @Operation(summary = "Submit a test case for review")
    public Object submitForReview(@PathVariable String projectId,
                                  @PathVariable String tcId) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/submit-review")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/{tcId}/approve")
    @Operation(summary = "Approve a test case")
    public Object approveTestCase(@PathVariable String projectId,
                                  @PathVariable String tcId) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/{tcId}/reject")
    @Operation(summary = "Reject a test case")
    public Object rejectTestCase(@PathVariable String projectId,
                                 @PathVariable String tcId) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/{tcId}/generate-automation")
    @Operation(summary = "Trigger automation generation for a test case via Agent")
    public Object generateAutomation(@PathVariable String projectId,
                                     @PathVariable String tcId,
                                     @RequestParam(required = false) String githubConfigId) {
        String uri = "/hub/test-cases/" + projectId + "/" + tcId + "/generate-automation";
        if (githubConfigId != null) uri += "?githubConfigId=" + githubConfigId;
        return agentClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/generate")
    @Operation(summary = "AI-generate test cases from requirements for a project")
    public Object generateTestCases(@PathVariable String projectId,
                                    @RequestBody Object body) {
        return agentClient.post()
                .uri("/hub/test-cases/" + projectId + "/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/{tcId}/link-requirement/{requirementId}")
    @Operation(summary = "Link a requirement to a test case")
    public Object linkRequirement(@PathVariable String projectId,
                                  @PathVariable String tcId,
                                  @PathVariable String requirementId) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/link-requirement/" + requirementId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().body(Object.class);
    }

    @DeleteMapping("/test-cases/{tcId}/link-requirement/{requirementId}")
    @Operation(summary = "Unlink a requirement from a test case")
    public Object unlinkRequirement(@PathVariable String projectId,
                                    @PathVariable String tcId,
                                    @PathVariable String requirementId) {
        return ingestionClient.delete()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/link-requirement/" + requirementId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/{tcId}/apply-suggestion")
    @Operation(summary = "Apply an impact analysis suggestion to a test case")
    public Object applyAnalysisSuggestion(@PathVariable String projectId,
                                          @PathVariable String tcId,
                                          @RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/apply-suggestion")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    // ── Test Runs ──────────────────────────────────────────────────────────────

    @GetMapping("/test-runs")
    @Operation(summary = "List test runs for a project")
    public Object listTestRuns(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/test-runs")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-runs")
    @Operation(summary = "Create a test run")
    public Object createTestRun(@PathVariable String projectId,
                                @RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @GetMapping("/test-runs/{runId}")
    @Operation(summary = "Get a test run by ID")
    public Object getTestRun(@PathVariable String projectId,
                             @PathVariable String runId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @DeleteMapping("/test-runs/{runId}")
    @Operation(summary = "Delete a test run")
    public ResponseEntity<Void> deleteTestRun(@PathVariable String projectId,
                                              @PathVariable String runId) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId)
                .retrieve().toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-runs/{runId}/complete")
    @Operation(summary = "Mark a test run as completed")
    public Object completeTestRun(@PathVariable String projectId,
                                  @PathVariable String runId) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().body(Object.class);
    }

    @GetMapping("/test-runs/{runId}/executions")
    @Operation(summary = "List test case executions for a test run")
    public Object listRunExecutions(@PathVariable String projectId,
                                    @PathVariable String runId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/executions")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PutMapping("/test-runs/{runId}/executions/{execId}")
    @Operation(summary = "Update a test case execution result")
    public Object updateExecution(@PathVariable String projectId,
                                  @PathVariable String runId,
                                  @PathVariable String execId,
                                  @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/executions/" + execId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    // ── Test Case Tags ───────────────────────────────────────────────────────────

    @GetMapping("/tags")
    @Operation(summary = "Distinct test-case tags in a project (typeahead suggestions)")
    public Object tagSuggestions(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/tags")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @GetMapping("/test-cases/{tcId}/tags")
    @Operation(summary = "List tags on a test case")
    public Object listTags(@PathVariable String projectId, @PathVariable String tcId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/tags")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/test-cases/{tcId}/tags")
    @Operation(summary = "Add a tag to a test case")
    public Object addTag(@PathVariable String projectId, @PathVariable String tcId,
                         @RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @DeleteMapping("/test-cases/{tcId}/tags/{name}")
    @Operation(summary = "Remove a tag from a test case")
    public ResponseEntity<Void> removeTag(@PathVariable String projectId, @PathVariable String tcId,
                                          @PathVariable String name) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/tags/" + name)
                .retrieve().toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    // ── Requirements Coverage ────────────────────────────────────────────────────

    @GetMapping("/coverage")
    @Operation(summary = "Requirements coverage matrix for a project")
    public Object coverage(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/coverage")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    // ── Environments ─────────────────────────────────────────────────────────────

    @GetMapping("/environments")
    @Operation(summary = "List environments for a project")
    public Object listEnvironments(@PathVariable String projectId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/environments")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PostMapping("/environments")
    @Operation(summary = "Create an environment")
    public Object createEnvironment(@PathVariable String projectId, @RequestBody Object body) {
        return ingestionClient.post()
                .uri("/api/v1/projects/" + projectId + "/environments")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }

    @DeleteMapping("/environments/{envId}")
    @Operation(summary = "Delete an environment")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable String projectId,
                                                  @PathVariable String envId) {
        ingestionClient.delete()
                .uri("/api/v1/projects/" + projectId + "/environments/" + envId)
                .retrieve().toBodilessEntity();
        return ResponseEntity.noContent().build();
    }

    // ── Test Case Properties (parametrization axes) ──────────────────────────────

    @GetMapping("/test-cases/{tcId}/properties")
    @Operation(summary = "List parametrization properties of a test case")
    public Object listProperties(@PathVariable String projectId, @PathVariable String tcId) {
        return ingestionClient.get()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/properties")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Object.class);
    }

    @PutMapping("/test-cases/{tcId}/properties")
    @Operation(summary = "Replace parametrization properties of a test case")
    public Object replaceProperties(@PathVariable String projectId, @PathVariable String tcId,
                                    @RequestBody Object body) {
        return ingestionClient.put()
                .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Object.class);
    }
}
