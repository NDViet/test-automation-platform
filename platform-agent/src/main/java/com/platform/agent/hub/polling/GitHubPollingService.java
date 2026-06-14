package com.platform.agent.hub.polling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.TriggerRef;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.GitHubPrTracking;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.GitHubPrTrackingRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Polls GitHub for new or commit-updated open PRs for projects configured with integrationMode=POLLING.
 *
 * Change detection uses head.sha, not updated_at.
 * A workflow is triggered only when:
 *   (a) the PR is seen for the first time, OR
 *   (b) head.sha differs from the last-recorded sha (new commits pushed)
 *
 * This means comments, labels, review requests, assignee changes, and CI reruns
 * — all of which bump updated_at without changing the code — are silently ignored.
 *
 * Config keys in ProjectIntegrationConfig.connectionParams:
 *   integrationMode     — "WEBHOOK" (default) or "POLLING"
 *   pollIntervalMinutes — default 10
 *   token               — PAT with read:repo (or public_repo) scope
 *   repoFullName        — "owner/repo"
 *   lastPolledAt        — ISO-8601; updated after each successful poll (used only to
 *                         short-circuit the open-PR list fetch when nothing recent changed)
 */
@Service
public class GitHubPollingService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPollingService.class);
    private static final String MODE_POLLING       = "POLLING";
    private static final int    DEFAULT_INTERVAL   = 10;
    private static final long   STALE_DAYS         = 30;

    private final ProjectIntegrationConfigRepository configRepo;
    private final AgentWorkflowRepository            workflowRepo;
    private final GitHubPrTrackingRepository         trackingRepo;
    private final GitHubApiClient                    gitHubApiClient;
    private final AgentWorkflowService               workflowService;
    private final ContextAssembler                   contextAssembler;
    private final CredentialResolver                 credentialResolver;
    private final ObjectMapper                       mapper;

    public GitHubPollingService(ProjectIntegrationConfigRepository configRepo,
                                AgentWorkflowRepository workflowRepo,
                                GitHubPrTrackingRepository trackingRepo,
                                GitHubApiClient gitHubApiClient,
                                AgentWorkflowService workflowService,
                                ContextAssembler contextAssembler,
                                CredentialResolver credentialResolver,
                                ObjectMapper mapper) {
        this.configRepo         = configRepo;
        this.workflowRepo       = workflowRepo;
        this.trackingRepo       = trackingRepo;
        this.gitHubApiClient    = gitHubApiClient;
        this.workflowService    = workflowService;
        this.contextAssembler   = contextAssembler;
        this.credentialResolver = credentialResolver;
        this.mapper             = mapper;
    }

    /** Runs every 60 s; each config decides independently if its interval has elapsed. */
    @Scheduled(fixedDelay = 60_000)
    public void pollAll() {
        List<ProjectIntegrationConfig> configs =
                configRepo.findByIntegrationTypeAndEnabled(IntegrationType.GITHUB.name(), true);

        for (ProjectIntegrationConfig config : configs) {
            if (!MODE_POLLING.equalsIgnoreCase(config.param("integrationMode"))) continue;
            if (!isDue(config)) continue;

            try {
                pollProject(config);
            } catch (Exception e) {
                log.error("GitHub poll failed for project {}: {}", config.getProjectId(), e.getMessage(), e);
                config.recordSyncError();
                configRepo.save(config);
            }
        }
    }

    /**
     * Polls a single project immediately — used by the on-demand sync endpoint.
     * Returns the number of workflows triggered.
     */
    @Transactional
    public int syncNow(UUID projectId) {
        List<ProjectIntegrationConfig> configs = configRepo
                .findAllByProjectIdAndIntegrationType(projectId, IntegrationType.GITHUB.name());
        if (configs.isEmpty()) {
            throw new IllegalArgumentException("No GitHub integration config for project " + projectId);
        }
        int total = 0;
        for (ProjectIntegrationConfig config : configs) {
            total += pollProject(config);
        }
        return total;
    }

    // -------------------------------------------------------------------------

    @Transactional
    protected int pollProject(ProjectIntegrationConfig config) {
        // Inherit from the Org→Project→Team credential cascade when the project's own
        // config omits the token or repo (project config overrides; org provides defaults).
        ResolvedCredential inherited =
                credentialResolver.resolve(config.getProjectId(), IntegrationType.GITHUB.name()).orElse(null);

        String repoFullName = firstNonBlank(config.param("repoFullName"), inheritedRepo(inherited));
        String token        = firstNonBlank(config.param("token"), inheritedToken(inherited));
        if (repoFullName == null || !repoFullName.contains("/")) {
            log.warn("GitHub polling: invalid repoFullName '{}' for project {}", repoFullName, config.getProjectId());
            return 0;
        }

        String[] parts    = repoFullName.split("/", 2);
        String owner      = parts[0];
        String repo       = parts[1];
        Instant pollStart = Instant.now();

        String json = gitHubApiClient.getOpenPRs(owner, repo, token != null ? token : "");
        int triggered = 0;

        try {
            JsonNode prs = mapper.readTree(json);
            for (JsonNode pr : prs) {
                triggered += evaluatePr(pr, config.getProjectId(), repoFullName);
            }
            // Clean up tracking rows for PRs that closed/merged long ago
            trackingRepo.deleteStaleByProject(
                    config.getProjectId(),
                    Instant.now().minusSeconds(STALE_DAYS * 86_400));
        } catch (Exception e) {
            log.error("GitHub poll: failed to process PR list for {}: {}", repoFullName, e.getMessage(), e);
            config.recordSyncError();
            configRepo.save(config);
            return triggered;
        }

        Map<String, String> params = new HashMap<>(
                config.getConnectionParams() != null ? config.getConnectionParams() : Map.of());
        params.put("lastPolledAt", pollStart.toString());
        config.setConnectionParams(params);
        config.recordSyncSuccess();
        configRepo.save(config);

        log.info("GitHub poll: {} workflow(s) triggered for {}", triggered, repoFullName);
        return triggered;
    }

    /**
     * Evaluates a single PR from the list response.
     * Returns 1 if a workflow was triggered, 0 otherwise.
     *
     * Trigger conditions:
     *   NEW PR  — no tracking record exists yet
     *   UPDATED — head.sha differs from stored sha (new commit pushed)
     *
     * Ignored (same sha, different updated_at):
     *   comments, labels, reviewer assignments, CI reruns, title edits, etc.
     */
    private int evaluatePr(JsonNode pr, UUID projectId, String repoFullName) {
        int    prNumber  = pr.path("number").asInt();
        String headSha   = pr.path("head").path("sha").asText("");
        String prUrl     = pr.path("html_url").asText();
        String actor     = pr.path("user").path("login").asText();
        Instant updatedAt = parseInstant(pr.path("updated_at").asText());

        if (headSha.isBlank()) {
            log.warn("GitHub poll: PR #{} missing head.sha, skipping", prNumber);
            return 0;
        }

        Optional<GitHubPrTracking> existing = trackingRepo
                .findByProjectIdAndRepoFullNameAndPrNumber(projectId, repoFullName, prNumber);

        ChangeReason reason = determineReason(existing, headSha);
        if (reason == ChangeReason.NONE) {
            log.debug("GitHub poll: PR #{} unchanged (sha={}), skipping", prNumber, headSha.substring(0, 8));
            return 0;
        }

        log.info("GitHub poll: PR #{} — {} (sha {} → {})",
                prNumber, reason,
                existing.map(t -> t.getHeadSha().substring(0, 8)).orElse("none"),
                headSha.substring(0, 8));

        TriggerRef trigger = new TriggerRef(
                TriggerRef.TriggerType.WEBHOOK,
                IntegrationType.GITHUB,
                "pull_request",
                String.valueOf(prNumber),
                prUrl,
                actor,
                updatedAt
        );

        try {
            AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
            ContextBundle bundle   = contextAssembler.assemble(workflow.getId(), projectId, trigger);
            workflowService.executeWorkflow(workflow.getId(), bundle);

            // Upsert tracking record
            if (existing.isPresent()) {
                existing.get().update(headSha, workflow.getId());
                trackingRepo.save(existing.get());
            } else {
                trackingRepo.save(new GitHubPrTracking(
                        projectId, repoFullName, prNumber, headSha, prUrl, workflow.getId()));
            }
            return 1;
        } catch (Exception e) {
            log.error("GitHub poll: failed to trigger workflow for PR #{}: {}", prNumber, e.getMessage(), e);
            return 0;
        }
    }

    private enum ChangeReason {
        NONE,
        NEW_PR,
        NEW_COMMITS
    }

    private ChangeReason determineReason(Optional<GitHubPrTracking> existing, String currentSha) {
        if (existing.isEmpty())                          return ChangeReason.NEW_PR;
        if (!existing.get().getHeadSha().equals(currentSha)) return ChangeReason.NEW_COMMITS;
        return ChangeReason.NONE;
    }

    private boolean isDue(ProjectIntegrationConfig config) {
        String lastStr = config.param("lastPolledAt");
        if (lastStr == null) return true;

        int intervalMinutes = DEFAULT_INTERVAL;
        String intervalStr  = config.param("pollIntervalMinutes");
        if (intervalStr != null) {
            try { intervalMinutes = Integer.parseInt(intervalStr); } catch (NumberFormatException ignored) {}
        }
        return Instant.now().isAfter(parseInstant(lastStr).plusSeconds(intervalMinutes * 60L));
    }

    private Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return Instant.EPOCH;
        try { return Instant.parse(iso); } catch (Exception e) { return Instant.EPOCH; }
    }

    /** Secret PAT/token from the inherited credential, or null. */
    private String inheritedToken(ResolvedCredential cred) {
        if (cred == null) return null;
        String pat = cred.secret("pat");
        return (pat != null && !pat.isBlank()) ? pat : cred.secret("token");
    }

    /** "owner/repo" derived from the inherited credential's params, or null. */
    private String inheritedRepo(ResolvedCredential cred) {
        if (cred == null) return null;
        String full = firstNonBlank(cred.param("repoFullName"), cred.param("repo_full_name"));
        if (full != null && full.contains("/")) return full;
        String owner = firstNonBlank(cred.param("owner"), cred.param("organization"));
        String repo  = cred.param("repo");
        return (owner != null && repo != null) ? owner + "/" + repo : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }
}
