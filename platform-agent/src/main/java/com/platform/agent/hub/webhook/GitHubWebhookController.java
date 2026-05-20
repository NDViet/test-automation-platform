package com.platform.agent.hub.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.TriggerRef;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Receives GitHub PR webhook events, verifies HMAC signature, finds the matching
 * platform project by repoFullName, and triggers an agent workflow asynchronously.
 */
@RestController
@RequestMapping("/hub/webhooks/github")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private static final Set<String> HANDLED_ACTIONS = Set.of("opened", "synchronize", "reopened");

    private final HmacVerifier hmacVerifier;
    private final ObjectMapper mapper;
    private final AgentWorkflowService workflowService;
    private final ContextAssembler contextAssembler;
    private final ProjectIntegrationConfigRepository projectIntegrationConfigRepo;

    public GitHubWebhookController(HmacVerifier hmacVerifier,
                                    ObjectMapper mapper,
                                    AgentWorkflowService workflowService,
                                    ContextAssembler contextAssembler,
                                    ProjectIntegrationConfigRepository projectIntegrationConfigRepo) {
        this.hmacVerifier                 = hmacVerifier;
        this.mapper                       = mapper;
        this.workflowService              = workflowService;
        this.contextAssembler             = contextAssembler;
        this.projectIntegrationConfigRepo = projectIntegrationConfigRepo;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader("X-Hub-Signature-256") String sig,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestBody String payload) {

        // 1. Verify HMAC signature
        if (!hmacVerifier.verify(payload, sig)) {
            log.warn("GitHub webhook: invalid HMAC signature");
            return ResponseEntity.status(401).build();
        }

        // 2. Only handle pull_request events
        if (!"pull_request".equals(event)) {
            log.debug("GitHub webhook: ignoring event type '{}'", event);
            return ResponseEntity.ok().build();
        }

        try {
            JsonNode root = mapper.readTree(payload);

            // 3. Parse action — only process relevant PR actions
            String action = root.path("action").asText("");
            if (!HANDLED_ACTIONS.contains(action)) {
                log.debug("GitHub webhook: ignoring pull_request action '{}'", action);
                return ResponseEntity.ok().build();
            }

            int prNumber        = root.path("number").asInt();
            String repoFullName = root.path("repository").path("full_name").asText();
            String prUrl        = root.path("pull_request").path("html_url").asText();
            String senderLogin  = root.path("sender").path("login").asText();

            // 4. Find the ProjectIntegrationConfig whose connectionParams["repoFullName"] matches
            Optional<ProjectIntegrationConfig> configOpt = findConfigByRepoFullName(repoFullName);
            if (configOpt.isEmpty()) {
                log.debug("GitHub webhook: no platform project tracks repo '{}', ignoring", repoFullName);
                return ResponseEntity.ok().build();
            }

            ProjectIntegrationConfig config = configOpt.get();
            UUID projectId = config.getProjectId();

            // 5. Build TriggerRef
            TriggerRef trigger = new TriggerRef(
                    TriggerRef.TriggerType.WEBHOOK,
                    IntegrationType.GITHUB,
                    "pull_request",
                    String.valueOf(prNumber),
                    prUrl,
                    senderLogin,
                    Instant.now()
            );

            log.info("GitHub webhook: PR #{} action='{}' repo='{}' project={} — triggering workflow",
                    prNumber, action, repoFullName, projectId);

            // 6. Create workflow, assemble context, execute asynchronously
            AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
            ContextBundle bundle   = contextAssembler.assemble(workflow.getId(), projectId, trigger);
            workflowService.executeWorkflow(workflow.getId(), bundle);

            // 7. Return 202 Accepted — workflow is running asynchronously
            return ResponseEntity.accepted().build();

        } catch (Exception e) {
            log.error("GitHub webhook: failed to process pull_request event", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Finds a GITHUB ProjectIntegrationConfig whose connectionParams["repoFullName"]
     * matches the given repoFullName. Loads all GITHUB configs and filters in Java
     * (acceptable: small number of configs per org).
     */
    private Optional<ProjectIntegrationConfig> findConfigByRepoFullName(String repoFullName) {
        List<ProjectIntegrationConfig> all = projectIntegrationConfigRepo.findAll();
        return all.stream()
                .filter(c -> IntegrationType.GITHUB.name().equals(c.getIntegrationType()))
                .filter(c -> c.getConnectionParams() != null)
                .filter(c -> repoFullName.equals(c.getConnectionParams().get("repoFullName")))
                .findFirst();
    }
}
