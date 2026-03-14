package com.platform.integration.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.integration.port.IssueReference;
import com.platform.integration.port.IssueRequest;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.IssueUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * JIRA Cloud implementation of {@link IssueTrackerPort}.
 *
 * <p>One instance is created per {@code IntegrationConfig} row — not a Spring bean.
 * Instantiated by {@link JiraTrackerFactory}.</p>
 */
public class JiraIssueTracker implements IssueTrackerPort {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueTracker.class);

    static final String TYPE = "JIRA";

    private final JiraClient client;
    private final JiraIssueMapper mapper;
    private final String projectKey;
    private final String doneTransitionName;
    private final String reopenTransitionName;
    private final String baseUrl;

    public JiraIssueTracker(JiraClient client, JiraIssueMapper mapper,
                             String projectKey, String doneTransitionName,
                             String reopenTransitionName, String baseUrl) {
        this.client               = client;
        this.mapper               = mapper;
        this.projectKey           = projectKey;
        this.doneTransitionName   = doneTransitionName;
        this.reopenTransitionName = reopenTransitionName;
        this.baseUrl              = baseUrl;
    }

    @Override
    public String trackerType() { return TYPE; }

    @Override
    public IssueReference createIssue(IssueRequest request) {
        String body = mapper.toCreateBody(request);
        JsonNode response = client.createIssue(body);
        String key  = response.path("key").asText();
        String url  = baseUrl + "/browse/" + key;
        log.info("[JIRA] Created issue {} — {}", key, request.title());
        return new IssueReference(key, url, "Open", request.issueType());
    }

    @Override
    public void updateIssue(String issueKey, IssueUpdate update) {
        String commentBody = mapper.toCommentBody(update.comment());
        client.addComment(issueKey, commentBody);
        log.debug("[JIRA] Added comment to {}", issueKey);
    }

    @Override
    public void closeIssue(String issueKey, String comment) {
        try {
            String transitionId = client.findTransitionId(issueKey, doneTransitionName);
            client.transition(issueKey, transitionId);
            client.addComment(issueKey, mapper.toCommentBody(comment));
            log.info("[JIRA] Closed issue {}", issueKey);
        } catch (Exception e) {
            log.warn("[JIRA] Failed to close issue {}: {}", issueKey, e.getMessage());
            throw e;
        }
    }

    @Override
    public void reopenIssue(String issueKey, String comment) {
        try {
            String transitionId = client.findTransitionId(issueKey, reopenTransitionName);
            client.transition(issueKey, transitionId);
            client.addComment(issueKey, mapper.toCommentBody(comment));
            log.info("[JIRA] Reopened issue {}", issueKey);
        } catch (Exception e) {
            log.warn("[JIRA] Failed to reopen issue {}: {}", issueKey, e.getMessage());
            throw e;
        }
    }

    @Override
    public Optional<IssueReference> findOpenIssue(String testId, String jiraProjectKey) {
        String safeLabel = testId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String jql = "project = \"" + jiraProjectKey + "\" "
                + "AND labels = \"" + safeLabel + "\" "
                + "AND status != \"Done\"";
        try {
            JsonNode result = client.searchIssues(jql);
            JsonNode issues = result.path("issues");
            if (issues.isArray() && issues.size() > 0) {
                JsonNode issue  = issues.get(0);
                String key      = issue.path("key").asText();
                String status   = issue.path("fields").path("status").path("name").asText("Open");
                String url      = baseUrl + "/browse/" + key;
                return Optional.of(new IssueReference(key, url, status, "Bug"));
            }
        } catch (Exception e) {
            log.warn("[JIRA] Search failed for label={}: {}", safeLabel, e.getMessage());
        }
        return Optional.empty();
    }
}
