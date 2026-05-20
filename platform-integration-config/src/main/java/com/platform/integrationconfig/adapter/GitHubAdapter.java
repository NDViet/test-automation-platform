package com.platform.integrationconfig.adapter;

import com.platform.common.integration.*;
import com.platform.common.model.AutomatedTestRef;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/**
 * IntegrationAdapter for GitHub (REST API v3 + GraphQL).
 * Tier 3: source code repository — fetches AutomatedTestRefs and handles PR webhook events.
 */
@Component
public class GitHubAdapter implements IntegrationAdapter<AutomatedTestRef, GitHubAdapter.GitHubCommand> {

    private final RestClient.Builder restClientBuilder;

    public GitHubAdapter(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public IntegrationType type() {
        return IntegrationType.GITHUB;
    }

    @Override
    public PagedRecords<AutomatedTestRef> fetchPage(SourceIntegrationConfig config, SyncCursor cursor) {
        // TODO: scan test files in repo via GitHub Contents API or Tree API
        return PagedRecords.empty(cursor.advance(Instant.now(), null));
    }

    @Override
    public String push(SourceIntegrationConfig config, GitHubCommand command) {
        // TODO: create PR, push commit, or post PR review comment
        return null;
    }

    @Override
    public IntegrationHealth healthCheck(SourceIntegrationConfig config) {
        // TODO: GET /repos/{owner}/{repo} to validate token and repo access
        return IntegrationHealth.healthy();
    }

    @Override
    public List<AutomatedTestRef> fromWebhook(WebhookEvent event, SourceIntegrationConfig config) {
        // TODO: handle pull_request.opened / pull_request.synchronize events
        return List.of();
    }

    /**
     * Command for GitHub operations.
     */
    public record GitHubCommand(
            Op op,
            String owner,
            String repo,
            String branch,
            String path,           // file path for COMMIT_FILE
            String content,        // base64 or raw for COMMIT_FILE
            String commitMessage,
            String prNumber,       // for PR_COMMENT
            String reviewBody
    ) {
        public enum Op { COMMIT_FILE, CREATE_PR, POST_PR_COMMENT }
    }
}
