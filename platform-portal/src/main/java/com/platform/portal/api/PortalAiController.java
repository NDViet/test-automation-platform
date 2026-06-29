package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/** Portal BFF — AI settings and on-demand analysis. */
@RestController
@RequestMapping("/api/portal/ai")
@Tag(name = "Portal AI", description = "AI settings and on-demand analysis for the portal")
public class PortalAiController {

  private final RestClient aiClient;
  private final RestClient agentClient;

  public PortalAiController(
      @Qualifier("aiClient") RestClient aiClient,
      @Qualifier("agentClient") RestClient agentClient) {
    this.aiClient = aiClient;
    this.agentClient = agentClient;
  }

  @GetMapping("/settings")
  @Operation(summary = "Get AI provider settings")
  public Object getSettings() {
    return aiClient
        .get()
        .uri("/api/v1/ai/settings")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/settings")
  @Operation(summary = "Update AI provider settings")
  public Object updateSettings(@RequestBody Map<String, Object> body) {
    return aiClient
        .put()
        .uri("/api/v1/ai/settings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/settings/test")
  @Operation(summary = "Test AI provider connectivity")
  public Object testConnection(@RequestBody Map<String, Object> body) {
    return aiClient
        .post()
        .uri("/api/v1/ai/settings/test")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/settings/scoped/effective")
  @Operation(summary = "Effective (merged Org→Team→Project) AI settings for a project")
  public Object effectiveScopedSettings(@RequestParam String projectId) {
    return aiClient
        .get()
        .uri("/api/v1/ai/settings/scoped/effective?projectId=" + projectId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/settings/scoped/{scope}/{scopeId}")
  @Operation(summary = "Set a per-team/per-project AI setting override")
  public Object setScopedSetting(
      @PathVariable String scope,
      @PathVariable String scopeId,
      @RequestBody Map<String, Object> body) {
    aiClient
        .put()
        .uri("/api/v1/ai/settings/scoped/" + scope + "/" + scopeId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
    return Map.of("status", "ok");
  }

  @PostMapping("/projects/{projectId}/results/{resultId}/analyse")
  @Operation(summary = "On-demand AI analysis for a test result")
  public Object analyseResult(@PathVariable String projectId, @PathVariable String resultId) {
    return aiClient
        .post()
        .uri("/api/v1/projects/" + projectId + "/results/" + resultId + "/analyse")
        .contentType(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/analyse/run-now")
  @Operation(summary = "Trigger on-demand batch analysis of all unanalysed failures")
  public Object runNow(@RequestParam(defaultValue = "24") int hours) {
    return aiClient
        .post()
        .uri("/api/v1/analyse/run-now?hours=" + hours)
        .contentType(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  // ── AI Skills (project-scoped reusable instruction sets) ───────────────────
  // Proxy to platform-agent /hub/projects/{projectId}/ai/skills.

  @GetMapping("/projects/{projectId}/skills")
  @Operation(summary = "List AI generation skills for a project")
  public Object listSkills(@PathVariable String projectId) {
    return agentClient
        .get()
        .uri("/hub/projects/" + projectId + "/ai/skills")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/projects/{projectId}/skills")
  @Operation(summary = "Create an AI generation skill")
  public Object createSkill(
      @PathVariable String projectId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return agentClient
        .post()
        .uri("/hub/projects/" + projectId + "/ai/skills")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", actor == null ? "" : actor)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/projects/{projectId}/skills/{skillId}")
  @Operation(summary = "Update an AI generation skill")
  public Object updateSkill(
      @PathVariable String projectId,
      @PathVariable String skillId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return agentClient
        .put()
        .uri("/hub/projects/" + projectId + "/ai/skills/" + skillId)
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", actor == null ? "" : actor)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/projects/{projectId}/skills/{skillId}")
  @Operation(summary = "Delete an AI generation skill")
  public void deleteSkill(@PathVariable String projectId, @PathVariable String skillId) {
    agentClient
        .delete()
        .uri("/hub/projects/" + projectId + "/ai/skills/" + skillId)
        .retrieve()
        .toBodilessEntity();
  }

  // ── AI prompt templates (project-scoped SYSTEM/USER templates) ─────────────
  // Proxy to platform-agent /hub/projects/{projectId}/ai/prompt-templates.

  @GetMapping("/projects/{projectId}/prompt-templates")
  @Operation(summary = "List AI generation prompt templates for a project")
  public Object listPromptTemplates(@PathVariable String projectId) {
    return agentClient
        .get()
        .uri("/hub/projects/" + projectId + "/ai/prompt-templates")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/projects/{projectId}/prompt-templates/defaults")
  @Operation(summary = "Resolved default SYSTEM + USER prompt bodies")
  public Object promptDefaults(@PathVariable String projectId) {
    return agentClient
        .get()
        .uri("/hub/projects/" + projectId + "/ai/prompt-templates/defaults")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/projects/{projectId}/prompt-templates")
  @Operation(summary = "Create an AI generation prompt template")
  public Object createPromptTemplate(
      @PathVariable String projectId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return agentClient
        .post()
        .uri("/hub/projects/" + projectId + "/ai/prompt-templates")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", actor == null ? "" : actor)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/projects/{projectId}/prompt-templates/{id}")
  @Operation(summary = "Update an AI generation prompt template")
  public Object updatePromptTemplate(
      @PathVariable String projectId,
      @PathVariable String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return agentClient
        .put()
        .uri("/hub/projects/" + projectId + "/ai/prompt-templates/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", actor == null ? "" : actor)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/projects/{projectId}/prompt-templates/{id}")
  @Operation(summary = "Delete an AI generation prompt template")
  public void deletePromptTemplate(@PathVariable String projectId, @PathVariable String id) {
    agentClient
        .delete()
        .uri("/hub/projects/" + projectId + "/ai/prompt-templates/" + id)
        .retrieve()
        .toBodilessEntity();
  }
}
