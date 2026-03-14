package com.platform.integration.port;

/**
 * Reference to a created or fetched issue.
 */
public record IssueReference(
        String key,
        String url,
        String status,
        String type
) {}
