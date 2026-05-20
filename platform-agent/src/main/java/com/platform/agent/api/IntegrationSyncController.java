package com.platform.agent.api;

import com.platform.agent.hub.polling.GitHubPollingService;
import com.platform.agent.hub.polling.JiraPollingService;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * On-demand sync trigger for any configured integration type.
 * GitHub: immediately polls for new/updated PRs via GitHubPollingService.
 * Jira/Linear: returns actionable guidance (API-client polling not yet implemented).
 */
@RestController
@RequestMapping("/api/agent/projects")
public class IntegrationSyncController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationSyncController.class);

    private final ProjectIntegrationConfigRepository configRepo;
    private final GitHubPollingService gitHubPollingService;
    private final JiraPollingService   jiraPollingService;

    public IntegrationSyncController(ProjectIntegrationConfigRepository configRepo,
                                     GitHubPollingService gitHubPollingService,
                                     JiraPollingService jiraPollingService) {
        this.configRepo           = configRepo;
        this.gitHubPollingService = gitHubPollingService;
        this.jiraPollingService   = jiraPollingService;
    }

    @PostMapping("/{projectId}/integrations/sync")
    public ResponseEntity<Map<String, Object>> syncNow(@PathVariable UUID projectId) {
        List<ProjectIntegrationConfig> configs = configRepo.findByProjectId(projectId);

        if (configs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No integration configs found for project " + projectId));
        }

        Map<String, Object> results = new java.util.LinkedHashMap<>();
        for (ProjectIntegrationConfig cfg : configs) {
            if (!cfg.isEnabled()) continue;
            String type = cfg.getIntegrationType();
            try {
                results.put(type, syncOne(projectId, type));
            } catch (Exception e) {
                log.error("sync failed for {} project {}: {}", type, projectId, e.getMessage(), e);
                results.put(type, Map.of("success", false, "error", e.getMessage()));
            }
        }

        return ResponseEntity.ok(Map.of("projectId", projectId.toString(), "results", results));
    }

    private Map<String, Object> syncOne(UUID projectId, String integrationType) {
        return switch (integrationType) {
            case "GITHUB" -> {
                int triggered = gitHubPollingService.syncNow(projectId);
                yield Map.of("success", true, "triggered", triggered,
                        "message", triggered == 0
                                ? "No new or updated PRs since last poll."
                                : triggered + " workflow(s) triggered.");
            }
            case "JIRA_CLOUD", "JIRA_SERVER" -> {
                int synced = jiraPollingService.syncNow(projectId, integrationType);
                yield Map.of("success", true, "synced", synced,
                        "message", synced == 0
                                ? "No new or updated issues since last sync."
                                : synced + " requirement(s) synced from Jira.");
            }
            case "LINEAR" -> Map.of(
                    "success", false,
                    "message", "Linear uses webhooks — trigger a test event from " +
                               "Settings → API → Webhooks in Linear to sync immediately.");
            default -> Map.of("success", false, "message", "Unknown integration type: " + integrationType);
        };
    }
}
