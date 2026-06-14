package com.platform.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.integration.port.IssueReference;
import com.platform.integration.port.IssueRequest;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.IssueUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * {@link IssueTrackerPort} backed by GitHub Issues.
 *
 * <p>The issue "key" is the issue number (as a String). Open issues are located
 * via a {@code testId:{testId}} label.</p>
 */
public class GitHubIssueTracker implements IssueTrackerPort {

    public static final String TRACKER_TYPE = "GITHUB_ISSUES";
    private static final Logger log = LoggerFactory.getLogger(GitHubIssueTracker.class);

    private final GitHubIssuesClient client;
    private final GitHubIssueMapper mapper;

    public GitHubIssueTracker(GitHubIssuesClient client, GitHubIssueMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String trackerType() {
        return TRACKER_TYPE;
    }

    @Override
    public IssueReference createIssue(IssueRequest request) {
        JsonNode resp = client.createIssue(mapper.toCreateBody(request));
        String number = resp.path("number").asText();
        String url = resp.path("html_url").asText();
        String state = resp.path("state").asText("open");
        log.info("[GitHubIssues] Created issue #{}", number);
        return new IssueReference(number, url, state, "issue");
    }

    @Override
    public void updateIssue(String issueKey, IssueUpdate update) {
        if (update.comment() != null && !update.comment().isBlank()) {
            client.addComment(issueKey, update.comment());
        }
        if (update.newStatus() != null && !update.newStatus().isBlank()) {
            client.setState(issueKey, normalizeState(update.newStatus()));
        }
    }

    @Override
    public void closeIssue(String issueKey, String comment) {
        if (comment != null && !comment.isBlank()) client.addComment(issueKey, comment);
        client.setState(issueKey, "closed");
    }

    @Override
    public void reopenIssue(String issueKey, String comment) {
        if (comment != null && !comment.isBlank()) client.addComment(issueKey, comment);
        client.setState(issueKey, "open");
    }

    @Override
    public Optional<IssueReference> findOpenIssue(String testId, String projectKey) {
        JsonNode resp = client.searchByLabel(mapper.testIdLabel(testId), "open");
        JsonNode items = resp.path("items");
        if (!items.isArray() || items.isEmpty()) return Optional.empty();
        JsonNode issue = items.get(0);
        return Optional.of(new IssueReference(
                issue.path("number").asText(),
                issue.path("html_url").asText(),
                issue.path("state").asText("open"),
                "issue"));
    }

    private String normalizeState(String s) {
        String v = s.toLowerCase();
        return (v.contains("close") || v.contains("done") || v.contains("resolve")) ? "closed" : "open";
    }
}
