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

  @PostMapping("/settings/models")
  @Operation(summary = "Fetch the models the configured LiteLLM gateway exposes")
  public Object fetchModels(@RequestBody(required = false) Map<String, Object> body) {
    return aiClient
        .post()
        .uri("/api/v1/ai/settings/models")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body != null ? body : Map.of())
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

  // ── Agents (org/project-scoped) ────────────────────────────────────────────
  // Proxy to platform-agent /hub/{scope}/{scopeId}/ai/agents (scope = orgs|projects).

  @GetMapping("/{scope}/{scopeId}/agents")
  @Operation(summary = "List agents at an org or project scope")
  public Object listAgents(@PathVariable String scope, @PathVariable String scopeId) {
    return agentClient
        .get()
        .uri("/hub/" + scope + "/" + scopeId + "/ai/agents")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/projects/{projectId}/agents/effective")
  @Operation(summary = "Effective agents for a project (own ∪ inherited org)")
  public Object effectiveAgents(@PathVariable String projectId) {
    return agentClient
        .get()
        .uri("/hub/projects/" + projectId + "/ai/agents/effective")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/{scope}/{scopeId}/agents")
  @Operation(summary = "Create an agent")
  public Object createAgent(
      @PathVariable String scope,
      @PathVariable String scopeId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return agentClient
        .post()
        .uri("/hub/" + scope + "/" + scopeId + "/ai/agents")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", actor == null ? "" : actor)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/{scope}/{scopeId}/agents/{id}")
  @Operation(summary = "Update an agent")
  public Object updateAgent(
      @PathVariable String scope,
      @PathVariable String scopeId,
      @PathVariable String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return agentClient
        .put()
        .uri("/hub/" + scope + "/" + scopeId + "/ai/agents/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", actor == null ? "" : actor)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/{scope}/{scopeId}/agents/{id}")
  @Operation(summary = "Delete an agent")
  public void deleteAgent(
      @PathVariable String scope,
      @PathVariable String scopeId,
      @PathVariable String id,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    agentClient
        .delete()
        .uri("/hub/" + scope + "/" + scopeId + "/ai/agents/" + id)
        .header("X-Actor", actor == null ? "" : actor)
        .retrieve()
        .toBodilessEntity();
  }

  // ── Task → agent assignments + sub-types ────────────────────────────────────

  @GetMapping("/{scope}/{scopeId}/task-agents")
  @Operation(summary = "List task→agent assignments at a scope")
  public Object listTaskAgents(@PathVariable String scope, @PathVariable String scopeId) {
    return agentClient
        .get()
        .uri("/hub/" + scope + "/" + scopeId + "/ai/task-agents")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @PutMapping("/{scope}/{scopeId}/task-agents")
  @Operation(summary = "Assign the default agent for a (task, sub-type)")
  public Object upsertTaskAgent(
      @PathVariable String scope,
      @PathVariable String scopeId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return agentClient
        .put()
        .uri("/hub/" + scope + "/" + scopeId + "/ai/task-agents")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Actor", actor == null ? "" : actor)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/{scope}/{scopeId}/task-agents/{id}")
  @Operation(summary = "Remove an assignment (revert to inherited/seed)")
  public void deleteTaskAgent(
      @PathVariable String scope,
      @PathVariable String scopeId,
      @PathVariable String id,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    agentClient
        .delete()
        .uri("/hub/" + scope + "/" + scopeId + "/ai/task-agents/" + id)
        .header("X-Actor", actor == null ? "" : actor)
        .retrieve()
        .toBodilessEntity();
  }

  @GetMapping("/task-subtypes")
  @Operation(summary = "Allowed sub-types for a task type")
  public Object taskSubTypes(@RequestParam String taskType) {
    return agentClient
        .get()
        .uri("/hub/ai/task-subtypes?taskType=" + taskType)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/projects/{projectId}/task-agents/effective")
  @Operation(summary = "Resolved default agent for a project task (source + agent)")
  public Object effectiveTaskAgent(
      @PathVariable String projectId,
      @RequestParam String taskType,
      @RequestParam(required = false) String subType) {
    String uri =
        "/hub/projects/"
            + projectId
            + "/ai/task-agents/effective?taskType="
            + taskType
            + (subType == null ? "" : "&subType=" + subType);
    return agentClient
        .get()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }
}
