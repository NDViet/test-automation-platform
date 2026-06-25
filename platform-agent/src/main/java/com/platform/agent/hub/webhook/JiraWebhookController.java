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
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives JIRA webhook events and triggers agent workflows.
 *
 * <p>JIRA Cloud webhooks do not sign payloads. Security is enforced via a shared secret in the
 * query parameter: POST /hub/webhooks/jira?secret={token}
 *
 * <p>Required JIRA webhook events: - jira:issue_created - jira:issue_updated (description/summary
 * changes only)
 */
@RestController
@RequestMapping("/hub/webhooks/jira")
public class JiraWebhookController {

  private static final Logger log = LoggerFactory.getLogger(JiraWebhookController.class);

  private static final Set<String> TRIGGER_EVENTS =
      Set.of("jira:issue_created", "jira:issue_updated");

  private static final Set<String> TRIGGER_FIELDS =
      Set.of("description", "summary", "Story Points", "Acceptance Criteria");

  @Value("${platform.agent.jira.webhook-secret:}")
  private String webhookSecret;

  private final ObjectMapper mapper;
  private final RequirementSyncService requirementSyncService;
  private final AgentWorkflowService workflowService;
  private final ContextAssembler contextAssembler;

  public JiraWebhookController(
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
      @RequestBody String payload) {

    // Verify shared secret (skip if not configured)
    if (!webhookSecret.isBlank() && !webhookSecret.equals(secret)) {
      log.warn("JIRA webhook: invalid secret");
      return ResponseEntity.status(401).build();
    }

    try {
      JsonNode root = mapper.readTree(payload);
      String webhookEvent = root.path("webhookEvent").asText("");

      if (!TRIGGER_EVENTS.contains(webhookEvent)) {
        log.debug("JIRA webhook: ignoring event '{}'", webhookEvent);
        return ResponseEntity.ok().build();
      }

      // For updates, only proceed if a relevant field changed
      if ("jira:issue_updated".equals(webhookEvent) && !hasRelevantFieldChange(root)) {
        log.debug("JIRA webhook: issue_updated with no relevant field change, skipping");
        return ResponseEntity.ok().build();
      }

      JsonNode issue = root.path("issue");
      String issueKey = issue.path("key").asText("");
      String issueUrl = issue.path("self").asText("");
      String actor = root.path("user").path("displayName").asText("jira");

      if (issueKey.isBlank()) {
        log.warn("JIRA webhook: missing issue key in payload");
        return ResponseEntity.ok().build();
      }

      // Upsert requirement before assembling context
      java.util.Optional<UUID> projectIdOpt = requirementSyncService.syncFromJira(payload);
      if (projectIdOpt.isEmpty()) {
        log.debug("JIRA webhook: no platform project tracks issue '{}', ignoring", issueKey);
        return ResponseEntity.ok().build();
      }

      UUID projectId = projectIdOpt.get();
      TriggerRef trigger =
          new TriggerRef(
              TriggerRef.TriggerType.WEBHOOK,
              IntegrationType.JIRA_CLOUD,
              "issue",
              issueKey,
              issueUrl,
              actor,
              Instant.now());

      log.info(
          "JIRA webhook: {} for issue '{}' — triggering workflow for project {}",
          webhookEvent,
          issueKey,
          projectId);

      AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
      ContextBundle bundle = contextAssembler.assemble(workflow.getId(), projectId, trigger);
      workflowService.executeWorkflow(workflow.getId(), bundle);

      return ResponseEntity.accepted().build();

    } catch (Exception e) {
      log.error("JIRA webhook: failed to process event", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  private boolean hasRelevantFieldChange(JsonNode root) {
    JsonNode changelog = root.path("changelog");
    if (changelog.isMissingNode()) return false;
    JsonNode items = changelog.path("items");
    if (!items.isArray()) return false;
    for (JsonNode item : items) {
      if (TRIGGER_FIELDS.contains(item.path("field").asText())) return true;
    }
    return false;
  }
}
