package com.platform.integrationconfig.adapter;

import com.platform.common.integration.*;
import com.platform.common.model.RequirementRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * IntegrationAdapter for JIRA Cloud (REST API v3).
 * Fetches issues as RequirementRecords and pushes issue create/update/transition commands.
 */
@Component
public class JiraCloudAdapter implements IntegrationAdapter<RequirementRecord, JiraCloudAdapter.JiraCommand> {

    private final RestClient.Builder restClientBuilder;

    public JiraCloudAdapter(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public IntegrationType type() {
        return IntegrationType.JIRA_CLOUD;
    }

    @Override
    public PagedRecords<RequirementRecord> fetchPage(SourceIntegrationConfig config, SyncCursor cursor) {
        // TODO: call JIRA REST API v3 /rest/api/3/search with JQL from filterConfig
        // Map issue fields to RequirementRecord using fieldMappings
        return PagedRecords.empty(cursor.advance(Instant.now(), null));
    }

    @Override
    public String push(SourceIntegrationConfig config, JiraCommand command) {
        // TODO: POST /rest/api/3/issue or PUT /rest/api/3/issue/{key} or POST transitions
        return null;
    }

    @Override
    public IntegrationHealth healthCheck(SourceIntegrationConfig config) {
        // TODO: GET /rest/api/3/myself to validate token + base_url
        return IntegrationHealth.healthy();
    }

    @Override
    public List<RequirementRecord> fromWebhook(WebhookEvent event, SourceIntegrationConfig config) {
        // TODO: parse jira:issue_created / jira:issue_updated webhook payload
        return List.of();
    }

    /**
     * Command for JIRA issue operations.
     */
    public record JiraCommand(
            Op op,
            String issueKey,           // null for CREATE
            String issueType,
            String summary,
            String description,
            String status,             // transition target
            Map<String, Object> customFields
    ) {
        public enum Op { CREATE, UPDATE, TRANSITION }
    }
}
