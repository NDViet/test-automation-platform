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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Receives Linear webhook events and triggers agent workflows.
 *
 * Linear signs payloads with HMAC-SHA256; the signature is in the
 * {@code linear-signature} header as a plain hex digest (no prefix).
 */
@RestController
@RequestMapping("/hub/webhooks/linear")
public class LinearWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LinearWebhookController.class);

    private static final Set<String> TRIGGER_ACTIONS = Set.of("create", "update");

    @Value("${platform.agent.linear.webhook-secret:}")
    private String webhookSecret;

    private final ObjectMapper mapper;
    private final RequirementSyncService requirementSyncService;
    private final AgentWorkflowService workflowService;
    private final ContextAssembler contextAssembler;

    public LinearWebhookController(ObjectMapper mapper,
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
            @RequestHeader(value = "linear-signature", defaultValue = "") String signature,
            @RequestBody String payload) {

        if (!verify(payload, signature)) {
            log.warn("Linear webhook: invalid HMAC signature");
            return ResponseEntity.status(401).build();
        }

        try {
            JsonNode root   = mapper.readTree(payload);
            String type     = root.path("type").asText("");
            String action   = root.path("action").asText("");

            // Only handle Issue events with create/update actions
            if (!"Issue".equals(type) || !TRIGGER_ACTIONS.contains(action)) {
                log.debug("Linear webhook: ignoring type='{}' action='{}'", type, action);
                return ResponseEntity.ok().build();
            }

            // For updates: only proceed if title or description changed
            if ("update".equals(action) && !hasRelevantUpdate(root)) {
                log.debug("Linear webhook: issue update with no relevant field change, skipping");
                return ResponseEntity.ok().build();
            }

            JsonNode data    = root.path("data");
            String identifier = data.path("identifier").asText("");
            String issueUrl   = data.path("url").asText("");
            String actor      = root.path("createdAt").asText("linear");   // actor not always present

            if (identifier.isBlank()) {
                log.warn("Linear webhook: missing identifier in payload");
                return ResponseEntity.ok().build();
            }

            java.util.Optional<UUID> projectIdOpt = requirementSyncService.syncFromLinear(payload);
            if (projectIdOpt.isEmpty()) {
                log.debug("Linear webhook: no platform project tracks identifier '{}', ignoring", identifier);
                return ResponseEntity.ok().build();
            }

            UUID projectId = projectIdOpt.get();
            TriggerRef trigger = new TriggerRef(
                    TriggerRef.TriggerType.WEBHOOK,
                    IntegrationType.LINEAR,
                    "issue",
                    identifier,
                    issueUrl,
                    actor,
                    Instant.now()
            );

            log.info("Linear webhook: {} action='{}' for '{}' — triggering workflow for project {}",
                    type, action, identifier, projectId);

            AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
            ContextBundle bundle   = contextAssembler.assemble(workflow.getId(), projectId, trigger);
            workflowService.executeWorkflow(workflow.getId(), bundle);

            return ResponseEntity.accepted().build();

        } catch (Exception e) {
            log.error("Linear webhook: failed to process event", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean verify(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) return true; // not configured
        if (signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            // Constant-time comparison
            if (expected.length() != signature.length()) return false;
            int diff = 0;
            for (int i = 0; i < expected.length(); i++) diff |= expected.charAt(i) ^ signature.charAt(i);
            return diff == 0;
        } catch (Exception e) {
            log.error("Linear HMAC verification failed", e);
            return false;
        }
    }

    /** Returns true if the update payload indicates title or description changed. */
    private boolean hasRelevantUpdate(JsonNode root) {
        JsonNode updatedFrom = root.path("updatedFrom");
        return updatedFrom.has("title") || updatedFrom.has("description");
    }
}
