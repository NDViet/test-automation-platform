package com.platform.integrationconfig.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.integration.*;
import com.platform.common.model.RequirementRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tier-1 IntegrationAdapter for GitHub Issues (requirements).
 *
 * <p>Distinct from {@link GitHubAdapter} (Tier 3 = source repos). Maps issues to
 * {@link RequirementRecord}s and handles {@code issues} webhook payloads.
 * Credentials: {@code connectionParams.pat} (+ {@code owner}, {@code repo} or
 * {@code repository}, optional {@code base_url} for GHE).</p>
 */
@Component
public class GitHubIssuesAdapter
        implements IntegrationAdapter<RequirementRecord, GitHubIssuesAdapter.GitHubIssueCommand> {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssuesAdapter.class);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper mapper;

    public GitHubIssuesAdapter(RestClient.Builder restClientBuilder, ObjectMapper mapper) {
        this.restClientBuilder = restClientBuilder;
        this.mapper            = mapper;
    }

    @Override
    public IntegrationType type() {
        return IntegrationType.GITHUB_ISSUES;
    }

    @Override
    public PagedRecords<RequirementRecord> fetchPage(SourceIntegrationConfig config, SyncCursor cursor) {
        try {
            String[] or = ownerRepo(config);
            JsonNode issues = client(config).get()
                    .uri("/repos/{o}/{r}/issues?state=all&per_page=50", or[0], or[1])
                    .retrieve().body(JsonNode.class);

            List<RequirementRecord> out = new ArrayList<>();
            if (issues != null && issues.isArray()) {
                for (JsonNode issue : issues) {
                    if (!issue.path("pull_request").isMissingNode()) continue; // skip PRs
                    out.add(toRecord(or[0] + "/" + or[1], issue));
                }
            }
            return new PagedRecords<>(out, cursor.advance(Instant.now(), null));
        } catch (Exception e) {
            log.warn("[GitHubIssuesAdapter] fetchPage failed: {}", e.getMessage());
            return PagedRecords.empty(cursor.advance(Instant.now(), null));
        }
    }

    @Override
    public String push(SourceIntegrationConfig config, GitHubIssueCommand command) {
        String[] or = ownerRepo(config);
        JsonNode resp = client(config).post()
                .uri("/repos/{o}/{r}/issues", or[0], or[1])
                .header("Content-Type", "application/json")
                .body(Map.of("title", command.title(),
                        "body", command.body() == null ? "" : command.body()))
                .retrieve().body(JsonNode.class);
        return resp == null ? null : resp.path("number").asText(null);
    }

    @Override
    public IntegrationHealth healthCheck(SourceIntegrationConfig config) {
        try {
            String[] or = ownerRepo(config);
            client(config).get().uri("/repos/{o}/{r}", or[0], or[1]).retrieve().body(String.class);
            return IntegrationHealth.healthy();
        } catch (Exception e) {
            return IntegrationHealth.down("GitHub Issues health check failed: " + e.getMessage(), 1);
        }
    }

    @Override
    public List<RequirementRecord> fromWebhook(WebhookEvent event, SourceIntegrationConfig config) {
        JsonNode root = mapper.valueToTree(event.rawPayload());
        JsonNode issue = root.path("issue");
        if (issue.isMissingNode()) return List.of();
        String repoFullName = root.path("repository").path("full_name").asText("");
        return List.of(toRecord(repoFullName, issue));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RequirementRecord toRecord(String repoFullName, JsonNode issue) {
        String number = issue.path("number").asText();
        String externalId = repoFullName + "#" + number;
        return new RequirementRecord(
                null, externalId, IntegrationType.GITHUB_ISSUES,
                issue.path("title").asText("(no title)"),
                issue.path("body").asText(""),
                List.of(), issueType(issue),
                issue.path("state").asText("open"), null,
                labels(issue), null, externalId, 0, Instant.now());
    }

    private List<String> labels(JsonNode issue) {
        List<String> out = new ArrayList<>();
        for (JsonNode l : issue.path("labels")) out.add(l.path("name").asText());
        return out;
    }

    private RequirementRecord.IssueType issueType(JsonNode issue) {
        for (JsonNode l : issue.path("labels")) {
            String n = l.path("name").asText("").toLowerCase();
            if (n.contains("epic")) return RequirementRecord.IssueType.EPIC;
            if (n.contains("bug") || n.contains("defect")) return RequirementRecord.IssueType.DEFECT;
            if (n.contains("spike")) return RequirementRecord.IssueType.SPIKE;
        }
        return RequirementRecord.IssueType.STORY;
    }

    private RestClient client(SourceIntegrationConfig config) {
        String base = config.param("base_url");
        if (base == null || base.isBlank()) base = "https://api.github.com";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String pat = config.param("pat");
        return restClientBuilder.clone().baseUrl(base)
                .defaultHeader("Authorization", "Bearer " + (pat == null ? "" : pat))
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    private String[] ownerRepo(SourceIntegrationConfig config) {
        String repository = config.param("repository");
        if (repository != null && repository.contains("/")) {
            return repository.split("/", 2);
        }
        return new String[]{config.param("owner"), config.param("repo")};
    }

    /** Minimal create command for the push path. */
    public record GitHubIssueCommand(String title, String body) {}
}
