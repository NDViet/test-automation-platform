package com.platform.integration.port;

import java.util.List;

/**
 * Tracker-agnostic issue creation request.
 */
public record IssueRequest(
        String title,
        String description,
        String issueType,   // "Bug", "Task"
        String priority,    // "High", "Medium", "Low"
        String projectKey,
        List<String> labels,
        String testId,
        String teamId
) {}
