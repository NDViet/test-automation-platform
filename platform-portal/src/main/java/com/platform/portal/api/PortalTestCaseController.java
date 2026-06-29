package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Portal BFF — Test Case Management (TCM): suites, managed test cases, test runs, executions. All
 * endpoints proxy to platform-ingestion (/api/v1/) or platform-agent (/hub/).
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}")
@Tag(name = "Portal TCM", description = "Test Case Management endpoints for the portal")
public class PortalTestCaseController {

  private final RestClient ingestionClient;
  private final RestClient agentClient;

  public PortalTestCaseController(
      @Qualifier("ingestionClient") RestClient ingestionClient,
      @Qualifier("agentClient") RestClient agentClient) {
    this.ingestionClient = ingestionClient;
    this.agentClient = agentClient;
  }

  // ── Test Suites ────────────────────────────────────────────────────────────

  @GetMapping("/test-suites")
  @Operation(summary = "List test suites for a project")
  public Object listTestSuites(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-suites")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-suites")
  @Operation(summary = "Create a test suite")
  public Object createTestSuite(@PathVariable String projectId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-suites")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-suites/{suiteId}")
  @Operation(summary = "Update a test suite")
  public Object updateTestSuite(
      @PathVariable String projectId, @PathVariable String suiteId, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-suites/" + suiteId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/test-suites/{suiteId}")
  @Operation(summary = "Delete a test suite")
  public ResponseEntity<Void> deleteTestSuite(
      @PathVariable String projectId, @PathVariable String suiteId) {
    ingestionClient
        .delete()
        .uri("/api/v1/projects/" + projectId + "/test-suites/" + suiteId)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/test-suites/{suiteId}/cases")
  @Operation(summary = "Resolved cases for a suite (static members or smart filter)")
  public Object suiteCases(@PathVariable String projectId, @PathVariable String suiteId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-suites/" + suiteId + "/cases")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-suites/{suiteId}/members")
  @Operation(summary = "Replace the static membership of a suite")
  public Object replaceSuiteMembers(
      @PathVariable String projectId, @PathVariable String suiteId, @RequestBody Object body) {
    ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-suites/" + suiteId + "/members")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
    return java.util.Map.of("status", "ok");
  }

  // ── Test Cases ─────────────────────────────────────────────────────────────

  @GetMapping("/test-cases")
  @Operation(summary = "List managed test cases for a project")
  public Object listTestCases(
      @PathVariable String projectId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String suiteId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String teamId,
      @RequestParam(required = false) String iteration) {
    StringBuilder uri =
        new StringBuilder("/api/v1/projects/").append(projectId).append("/test-cases");
    String sep = "?";
    sep = appendParam(uri, sep, "status", status);
    sep = appendParam(uri, sep, "suiteId", suiteId);
    sep = appendParam(uri, sep, "search", search);
    sep = appendParam(uri, sep, "area", area);
    sep = appendParam(uri, sep, "teamId", teamId);
    appendParam(uri, sep, "iteration", iteration);
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-cases/selectable")
  @Operation(summary = "Scope-filtered, searchable test cases for run creation")
  public Object selectableTestCases(
      @PathVariable String projectId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String iteration,
      @RequestParam(required = false) String teamId,
      @RequestParam(required = false) String q) {
    StringBuilder uri =
        new StringBuilder("/api/v1/projects/").append(projectId).append("/test-cases/selectable");
    String sep = "?";
    sep = appendParam(uri, sep, "status", status);
    sep = appendParam(uri, sep, "area", area);
    sep = appendParam(uri, sep, "iteration", iteration);
    sep = appendParam(uri, sep, "teamId", teamId);
    appendParam(uri, sep, "q", q);
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  // Append a raw query value; RestClient's URI template handling encodes it once.
  // (Pre-encoding here would double-encode, e.g. backslash %5C -> %255C.)
  private static String appendParam(StringBuilder uri, String sep, String key, String value) {
    if (value == null || value.isBlank()) return sep;
    uri.append(sep).append(key).append('=').append(value);
    return "&";
  }

  @PostMapping("/test-cases")
  @Operation(summary = "Create a managed test case")
  public Object createTestCase(@PathVariable String projectId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-cases")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-cases/{tcId}")
  @Operation(summary = "Get a managed test case by ID")
  public Object getTestCase(@PathVariable String projectId, @PathVariable String tcId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-cases/{tcId}")
  @Operation(summary = "Update a managed test case")
  public Object updateTestCase(
      @PathVariable String projectId, @PathVariable String tcId, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/test-cases/{tcId}")
  @Operation(summary = "Delete a managed test case")
  public ResponseEntity<Void> deleteTestCase(
      @PathVariable String projectId, @PathVariable String tcId) {
    ingestionClient
        .delete()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/test-cases/{tcId}/steps")
  @Operation(summary = "Replace steps of a test case")
  public Object replaceSteps(
      @PathVariable String projectId, @PathVariable String tcId, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/steps")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-cases/{tcId}/suites")
  @Operation(summary = "STATIC suite ids this case belongs to")
  public Object caseSuites(@PathVariable String projectId, @PathVariable String tcId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/suites")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-cases/{tcId}/suites")
  @Operation(summary = "Replace the case's suite memberships (a case may belong to many)")
  public ResponseEntity<Void> setCaseSuites(
      @PathVariable String projectId, @PathVariable String tcId, @RequestBody Object body) {
    ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/suites")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test-cases/{tcId}/submit-review")
  @Operation(summary = "Submit a test case for review")
  public Object submitForReview(@PathVariable String projectId, @PathVariable String tcId) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/submit-review")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body("{}")
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/{tcId}/approve")
  @Operation(summary = "Approve a test case")
  public Object approveTestCase(@PathVariable String projectId, @PathVariable String tcId) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/approve")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body("{}")
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/{tcId}/reject")
  @Operation(summary = "Reject a test case")
  public Object rejectTestCase(@PathVariable String projectId, @PathVariable String tcId) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/reject")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body("{}")
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/{tcId}/generate-automation")
  @Operation(summary = "Trigger automation generation for a test case via Agent")
  public Object generateAutomation(
      @PathVariable String projectId,
      @PathVariable String tcId,
      @RequestParam(required = false) String githubConfigId) {
    String uri = "/hub/test-cases/" + projectId + "/" + tcId + "/generate-automation";
    if (githubConfigId != null) uri += "?githubConfigId=" + githubConfigId;
    return agentClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body("{}")
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/generate")
  @Operation(summary = "AI-generate test cases from requirements for a project")
  public Object generateTestCases(@PathVariable String projectId, @RequestBody Object body) {
    return agentClient
        .post()
        .uri("/hub/test-cases/" + projectId + "/generate")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-cases/generations/{workflowId}")
  @Operation(summary = "Generation run status + clarification transcript")
  public Object generationStatus(
      @PathVariable String projectId, @PathVariable String workflowId) {
    return agentClient
        .get()
        .uri("/hub/test-cases/" + projectId + "/generations/" + workflowId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/generations/{workflowId}/answers")
  @Operation(summary = "Answer the agent's clarifying questions and resume generation")
  public Object answerGeneration(
      @PathVariable String projectId,
      @PathVariable String workflowId,
      @RequestBody Object body) {
    return agentClient
        .post()
        .uri("/hub/test-cases/" + projectId + "/generations/" + workflowId + "/answers")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping(
      value = "/test-cases/generation-files",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload a reference input file for AI test-case generation")
  public Object uploadGenerationFile(
      @PathVariable String projectId,
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-Actor", required = false) String actor)
      throws IOException {
    HttpHeaders partHeaders = new HttpHeaders();
    if (file.getContentType() != null) {
      partHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
    }
    ByteArrayResource bytes =
        new ByteArrayResource(file.getBytes()) {
          @Override
          public String getFilename() {
            return file.getOriginalFilename();
          }
        };
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("file", new HttpEntity<>(bytes, partHeaders));

    return agentClient
        .post()
        .uri("/hub/projects/" + projectId + "/ai/generation-files")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .headers(
            h -> {
              if (actor != null) h.set("X-Actor", actor);
            })
        .body(form)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/{tcId}/link-requirement/{requirementId}")
  @Operation(summary = "Link a requirement to a test case")
  public Object linkRequirement(
      @PathVariable String projectId,
      @PathVariable String tcId,
      @PathVariable String requirementId) {
    return ingestionClient
        .post()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-cases/"
                + tcId
                + "/link-requirement/"
                + requirementId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body("{}")
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/test-cases/{tcId}/link-requirement/{requirementId}")
  @Operation(summary = "Unlink a requirement from a test case")
  public Object unlinkRequirement(
      @PathVariable String projectId,
      @PathVariable String tcId,
      @PathVariable String requirementId) {
    return ingestionClient
        .delete()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-cases/"
                + tcId
                + "/link-requirement/"
                + requirementId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/{tcId}/apply-suggestion")
  @Operation(summary = "Apply an impact analysis suggestion to a test case")
  public Object applyAnalysisSuggestion(
      @PathVariable String projectId, @PathVariable String tcId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/apply-suggestion")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  // ── Test Runs ──────────────────────────────────────────────────────────────

  @GetMapping("/test-runs")
  @Operation(summary = "List test runs for a project")
  public Object listTestRuns(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-runs")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-runs")
  @Operation(summary = "Create a test run")
  public Object createTestRun(@PathVariable String projectId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-runs")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-runs/{runId}")
  @Operation(summary = "Get a test run by ID")
  public Object getTestRun(@PathVariable String projectId, @PathVariable String runId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/test-runs/{runId}")
  @Operation(summary = "Delete a test run")
  public ResponseEntity<Void> deleteTestRun(
      @PathVariable String projectId, @PathVariable String runId) {
    ingestionClient
        .delete()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test-runs/{runId}/complete")
  @Operation(summary = "Mark a test run as completed")
  public Object completeTestRun(@PathVariable String projectId, @PathVariable String runId) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body("{}")
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-runs/{runId}")
  @Operation(summary = "Edit an in-progress run's scope and environment")
  public Object updateTestRun(
      @PathVariable String projectId, @PathVariable String runId, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-runs/{runId}/reopen")
  @Operation(summary = "Reopen a completed test run for further editing")
  public Object reopenTestRun(@PathVariable String projectId, @PathVariable String runId) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/reopen")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body("{}")
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-runs/{runId}/cases")
  @Operation(summary = "Add existing approved cases to a live test run")
  public Object addTestRunCases(
      @PathVariable String projectId, @PathVariable String runId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/cases")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-runs/{runId}/executions")
  @Operation(summary = "List test case executions for a test run")
  public Object listRunExecutions(@PathVariable String projectId, @PathVariable String runId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/executions")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-runs/{runId}/executions/{execId}")
  @Operation(summary = "Update a test case execution result")
  public Object updateExecution(
      @PathVariable String projectId,
      @PathVariable String runId,
      @PathVariable String execId,
      @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-runs/" + runId + "/executions/" + execId)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-runs/{runId}/executions/{execId}/defect")
  @Operation(summary = "Link an existing ADO work item (defect) to a case execution")
  public Object linkDefect(
      @PathVariable String projectId,
      @PathVariable String runId,
      @PathVariable String execId,
      @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-runs/"
                + runId
                + "/executions/"
                + execId
                + "/defect")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/test-runs/{runId}/executions/{execId}/defect")
  @Operation(summary = "Unlink the ADO defect from a case execution")
  public Object unlinkDefect(
      @PathVariable String projectId, @PathVariable String runId, @PathVariable String execId) {
    return ingestionClient
        .delete()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-runs/"
                + runId
                + "/executions/"
                + execId
                + "/defect")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  // ── Evidence attachments ───────────────────────────────────────────────────

  @GetMapping("/test-runs/{runId}/executions/{execId}/attachments")
  @Operation(summary = "List evidence attachments for a case execution")
  public Object listAttachments(
      @PathVariable String projectId, @PathVariable String runId, @PathVariable String execId) {
    return ingestionClient
        .get()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-runs/"
                + runId
                + "/executions/"
                + execId
                + "/attachments")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping(
      value = "/test-runs/{runId}/executions/{execId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload an evidence file to a case execution")
  public Object uploadAttachment(
      @PathVariable String projectId,
      @PathVariable String runId,
      @PathVariable String execId,
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-Actor", required = false) String actor)
      throws IOException {
    HttpHeaders partHeaders = new HttpHeaders();
    if (file.getContentType() != null) {
      partHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
    }
    ByteArrayResource bytes =
        new ByteArrayResource(file.getBytes()) {
          @Override
          public String getFilename() {
            return file.getOriginalFilename();
          }
        };
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("file", new HttpEntity<>(bytes, partHeaders));

    return ingestionClient
        .post()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-runs/"
                + runId
                + "/executions/"
                + execId
                + "/attachments")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .headers(
            h -> {
              if (actor != null) h.set("X-Actor", actor);
            })
        .body(form)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-runs/{runId}/attachments/{attachmentId}/download")
  @Operation(summary = "Download an evidence attachment")
  public void downloadAttachment(
      @PathVariable String projectId,
      @PathVariable String runId,
      @PathVariable String attachmentId,
      HttpServletResponse response)
      throws IOException {
    ResponseEntity<byte[]> upstream =
        ingestionClient
            .get()
            .uri(
                "/api/v1/projects/"
                    + projectId
                    + "/test-runs/"
                    + runId
                    + "/attachments/"
                    + attachmentId
                    + "/download")
            .retrieve()
            .toEntity(byte[].class);
    byte[] body = upstream.getBody();
    if (body == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    MediaType ct = upstream.getHeaders().getContentType();
    response.setContentType(ct != null ? ct.toString() : "application/octet-stream");
    String disp = upstream.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    if (disp != null) response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disp);
    response.getOutputStream().write(body);
  }

  @DeleteMapping("/test-runs/{runId}/attachments/{attachmentId}")
  @Operation(summary = "Delete an evidence attachment")
  public ResponseEntity<Void> deleteAttachment(
      @PathVariable String projectId,
      @PathVariable String runId,
      @PathVariable String attachmentId) {
    ingestionClient
        .delete()
        .uri(
            "/api/v1/projects/"
                + projectId
                + "/test-runs/"
                + runId
                + "/attachments/"
                + attachmentId)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  // ── Test Case Tags ───────────────────────────────────────────────────────────

  @GetMapping("/tags")
  @Operation(summary = "Distinct test-case tags in a project (typeahead suggestions)")
  public Object tagSuggestions(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/tags")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-cases/{tcId}/tags")
  @Operation(summary = "List tags on a test case")
  public Object listTags(@PathVariable String projectId, @PathVariable String tcId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/tags")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/test-cases/{tcId}/tags")
  @Operation(summary = "Add a tag to a test case")
  public Object addTag(
      @PathVariable String projectId, @PathVariable String tcId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/tags")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/test-cases/{tcId}/tags/{name}")
  @Operation(summary = "Remove a tag from a test case")
  public ResponseEntity<Void> removeTag(
      @PathVariable String projectId, @PathVariable String tcId, @PathVariable String name) {
    ingestionClient
        .delete()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/tags/" + name)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  // ── Requirements Coverage ────────────────────────────────────────────────────

  @GetMapping("/coverage")
  @Operation(summary = "Requirements coverage matrix for a project (scoped by area/team/iteration)")
  public Object coverage(
      @PathVariable String projectId,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team,
      @RequestParam(required = false) String iteration) {
    StringBuilder uri =
        new StringBuilder("/api/v1/projects/").append(projectId).append("/coverage");
    String sep = "?";
    sep = appendParam(uri, sep, "area", area);
    sep = appendParam(uri, sep, "team", team);
    appendParam(uri, sep, "iteration", iteration);
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  // ── Environments ─────────────────────────────────────────────────────────────

  @GetMapping("/environments")
  @Operation(summary = "List environments for a project")
  public Object listEnvironments(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/environments")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/environments")
  @Operation(summary = "Create an environment")
  public Object createEnvironment(@PathVariable String projectId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/environments")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/environments/{envId}")
  @Operation(summary = "Delete an environment")
  public ResponseEntity<Void> deleteEnvironment(
      @PathVariable String projectId, @PathVariable String envId) {
    ingestionClient
        .delete()
        .uri("/api/v1/projects/" + projectId + "/environments/" + envId)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  // ── Test Case Properties (parametrization axes) ──────────────────────────────

  @GetMapping("/test-cases/{tcId}/properties")
  @Operation(summary = "List parametrization properties of a test case")
  public Object listProperties(@PathVariable String projectId, @PathVariable String tcId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/properties")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/test-cases/{tcId}/properties")
  @Operation(summary = "Replace parametrization properties of a test case")
  public Object replaceProperties(
      @PathVariable String projectId, @PathVariable String tcId, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/test-cases/" + tcId + "/properties")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  // ── Releases ─────────────────────────────────────────────────────────────────

  @GetMapping("/releases")
  @Operation(summary = "List releases for a project")
  public Object listReleases(@PathVariable String projectId) {
    return ingestionClient
        .get()
        .uri("/api/v1/projects/" + projectId + "/releases")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/releases")
  @Operation(summary = "Create a release")
  public Object createRelease(@PathVariable String projectId, @RequestBody Object body) {
    return ingestionClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/releases")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/releases/{id}")
  @Operation(summary = "Update a release")
  public Object updateRelease(
      @PathVariable String projectId, @PathVariable String id, @RequestBody Object body) {
    return ingestionClient
        .put()
        .uri("/api/v1/projects/" + projectId + "/releases/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/releases/{id}")
  @Operation(summary = "Delete a release")
  public ResponseEntity<Void> deleteRelease(
      @PathVariable String projectId, @PathVariable String id) {
    ingestionClient
        .delete()
        .uri("/api/v1/projects/" + projectId + "/releases/" + id)
        .retrieve()
        .toBodilessEntity();
    return ResponseEntity.noContent().build();
  }

  // ── Test Execution monitor (rollups by Release / Sprint / Area / Team) ────────

  @GetMapping("/test-execution-board")
  @Operation(summary = "Release board: releases grouped by team with pass-rate + coverage")
  public Object testExecutionBoard(
      @PathVariable String projectId,
      @RequestParam(required = false) String iteration,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team) {
    StringBuilder uri =
        new StringBuilder("/api/v1/projects/")
            .append(projectId)
            .append("/test-execution/release-board");
    String sep = "?";
    sep = appendParam(uri, sep, "iteration", iteration);
    sep = appendParam(uri, sep, "area", area);
    appendParam(uri, sep, "team", team);
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-execution/{dimension}")
  @Operation(summary = "Test execution rollup by dimension: by-release|by-sprint|by-area|by-team")
  public Object testExecutionByDimension(
      @PathVariable String projectId,
      @PathVariable String dimension,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team,
      @RequestParam(required = false) String iteration) {
    StringBuilder uri =
        new StringBuilder("/api/v1/projects/")
            .append(projectId)
            .append("/test-execution/")
            .append(dimension);
    String sep = "?";
    sep = appendParam(uri, sep, "area", area);
    sep = appendParam(uri, sep, "team", team);
    appendParam(uri, sep, "iteration", iteration);
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/test-execution-runs")
  @Operation(summary = "Drill-down: test runs for one dimension group")
  public Object testExecutionRuns(
      @PathVariable String projectId,
      @RequestParam String dimension,
      @RequestParam(required = false) String value) {
    StringBuilder uri =
        new StringBuilder(
            "/api/v1/projects/" + projectId + "/test-execution/runs?dimension=" + dimension);
    if (value != null && !value.isBlank()) {
      uri.append("&value=").append(value); // raw; RestClient encodes the URI once
    }
    return ingestionClient
        .get()
        .uri(uri.toString())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }
}
