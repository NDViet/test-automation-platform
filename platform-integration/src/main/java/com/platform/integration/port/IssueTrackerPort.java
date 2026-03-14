package com.platform.integration.port;

import java.util.Optional;

/**
 * Tracker-agnostic interface implemented by JIRA, Linear, GitHub, etc.
 */
public interface IssueTrackerPort {

    /** Identifies this tracker — matches {@code IntegrationConfig.trackerType}. */
    String trackerType();

    /** Creates a new issue and returns its reference. */
    IssueReference createIssue(IssueRequest request);

    /** Adds a comment (and optionally transitions status) on an existing issue. */
    void updateIssue(String issueKey, IssueUpdate update);

    /** Transitions the issue to Done and adds a comment. */
    void closeIssue(String issueKey, String comment);

    /** Reopens a Done/Closed issue and adds a comment. */
    void reopenIssue(String issueKey, String comment);

    /**
     * Searches for an existing open issue linked to {@code testId} in {@code projectKey}.
     * Returns empty if no open ticket is found.
     */
    Optional<IssueReference> findOpenIssue(String testId, String projectKey);
}
