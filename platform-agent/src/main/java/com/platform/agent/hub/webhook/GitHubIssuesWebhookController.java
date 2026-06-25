package com.platform.agent.hub.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.hub.sync.RequirementSyncService;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.TriggerRef;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.AgentWorkflow;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives GitHub {@code issues} webhook events and triggers agent workflows.
 *
 * <p>Security is a shared secret in the query parameter: {@code POST
 * /hub/webhooks/github-issues?secret={token}}. (For production, prefer validating the {@code
 * X-Hub-Signature-256} HMAC header.)
 */
@RestController
@RequestMapping("/hub/webhooks/github-issues")
public class GitHubIssuesWebhookController {

  private static final Logger log = LoggerFactory.getLogger(GitHubIssuesWebhookController.class);

  private static final Set<String> TRIGGER_ACTIONS = Set.of("opened", "edited", "reopened");

  @Value("${platform.agent.github-issues.webhook-secret:}")
  private String webhookSecret;

  private final ObjectMapper mapper;
  private final RequirementSyncService requirementSyncService;
  private final AgentWorkflowService workflowService;
  private final ContextAssembler contextAssembler;

  public GitHubIssuesWebhookController(
      ObjectMapper mapper,
      RequirementSyncService requirementSyncService,
      AgentWorkflowService workflowService,
      ContextAssembler contextAssembler) {
    this.mapper = mapper;
    this.requirementSyncService = requirementSyncService;
    this.workflowService = workflowService;
    this.contextAssembler = contextAssembler;
  }

  @PostMapping
  public ResponseEntity<Void> handle(
      @RequestParam(value = "secret", required = false, defaultValue = "") String secret,
      @RequestHeader(value = "X-GitHub-Event", required = false, defaultValue = "") String event,
      @RequestBody String payload) {

    if (!webhookSecret.isBlank() && !webhookSecret.equals(secret)) {
      log.warn("GitHub Issues webhook: invalid secret");
      return ResponseEntity.status(401).build();
    }
    if (!"issues".equals(event)) {
      log.debug("GitHub Issues webhook: ignoring event '{}'", event);
      return ResponseEntity.ok().build();
    }

    try {
      JsonNode root = mapper.readTree(payload);
      String action = root.path("action").asText("");
      if (!TRIGGER_ACTIONS.contains(action)) {
        log.debug("GitHub Issues webhook: ignoring action '{}'", action);
        return ResponseEntity.ok().build();
      }

      JsonNode issue = root.path("issue");
      String number = issue.path("number").asText("");
      String url = issue.path("html_url").asText("");
      String actor = root.path("sender").path("login").asText("github");
      String repo = root.path("repository").path("full_name").asText("");

      Optional<UUID> projectIdOpt = requirementSyncService.syncFromGitHubIssues(payload);
      if (projectIdOpt.isEmpty()) {
        log.debug("GitHub Issues webhook: no project tracks repo '{}', ignoring", repo);
        return ResponseEntity.ok().build();
      }

      UUID projectId = projectIdOpt.get();
      TriggerRef trigger =
          new TriggerRef(
              TriggerRef.TriggerType.WEBHOOK,
              IntegrationType.GITHUB_ISSUES,
              "issue",
              repo + "#" + number,
              url,
              actor,
              Instant.now());

      log.info(
          "GitHub Issues webhook: {} for {}#{} — triggering workflow for project {}",
          action,
          repo,
          number,
          projectId);

      AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
      ContextBundle bundle = contextAssembler.assemble(workflow.getId(), projectId, trigger);
      workflowService.executeWorkflow(workflow.getId(), bundle);

      return ResponseEntity.accepted().build();

    } catch (Exception e) {
      log.error("GitHub Issues webhook: failed to process event", e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
