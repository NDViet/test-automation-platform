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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Receives Azure DevOps Boards Service Hook events and triggers agent workflows.
 *
 * <p>Security: a shared secret is expected in the {@code X-ADO-Webhook-Secret} request header.
 * Configure the ADO Service Hook HTTP request with a custom header of that name.
 * If the property {@code platform.agent.azure-boards.webhook-secret} is blank, verification
 * is skipped (dev mode only — always set it in production).</p>
 *
 * <p>Configure two subscriptions in Azure DevOps: "Work item created" and
 * "Work item updated".</p>
 */
@RestController
@RequestMapping("/hub/webhooks/azure-boards")
public class AzureBoardsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AzureBoardsWebhookController.class);

    private static final Set<String> TRIGGER_EVENTS = Set.of(
            "workitem.created", "workitem.updated");

    @Value("${platform.agent.azure-boards.webhook-secret:}")
    private String webhookSecret;

    private final ObjectMapper mapper;
    private final RequirementSyncService requirementSyncService;
    private final AgentWorkflowService workflowService;
    private final ContextAssembler contextAssembler;

    public AzureBoardsWebhookController(ObjectMapper mapper,
                                        RequirementSyncService requirementSyncService,
                                        AgentWorkflowService workflowService,
                                        ContextAssembler contextAssembler) {
        this.mapper                 = mapper;
        this.requirementSyncService = requirementSyncService;
        this.workflowService        = workflowService;
        this.contextAssembler       = contextAssembler;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "X-ADO-Webhook-Secret", required = false, defaultValue = "") String secret,
            @RequestBody String payload) {

        if (!webhookSecret.isBlank() && !constantTimeEquals(webhookSecret, secret)) {
            log.warn("Azure Boards webhook: invalid secret");
            return ResponseEntity.status(401).build();
        }

        try {
            JsonNode root    = mapper.readTree(payload);
            String eventType = root.path("eventType").asText("");
            if (!TRIGGER_EVENTS.contains(eventType)) {
                log.debug("Azure Boards webhook: ignoring event '{}'", eventType);
                return ResponseEntity.ok().build();
            }

            JsonNode resource = root.path("resource");
            String workItemId = resource.path("id").asText(resource.path("workItemId").asText(""));
            String url        = resource.path("_links").path("html").path("href")
                    .asText(resource.path("url").asText(""));
            String actor      = root.path("resource").path("revisedBy").path("displayName")
                    .asText("azure-boards");

            Optional<UUID> projectIdOpt = requirementSyncService.syncFromAzureBoards(payload);
            if (projectIdOpt.isEmpty()) {
                log.debug("Azure Boards webhook: no project tracks work item '{}', ignoring", workItemId);
                return ResponseEntity.ok().build();
            }

            UUID projectId = projectIdOpt.get();
            TriggerRef trigger = new TriggerRef(
                    TriggerRef.TriggerType.WEBHOOK,
                    IntegrationType.AZURE_DEVOPS_BOARDS,
                    "workitem", workItemId, url, actor, Instant.now());

            log.info("Azure Boards webhook: {} for work item '{}' — triggering workflow for project {}",
                    eventType, workItemId, projectId);

            AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
            ContextBundle bundle   = contextAssembler.assemble(workflow.getId(), projectId, trigger);
            workflowService.executeWorkflow(workflow.getId(), bundle);

            return ResponseEntity.accepted().build();

        } catch (Exception e) {
            log.error("Azure Boards webhook: failed to process event", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
}
