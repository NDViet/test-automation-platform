package com.platform.integration.port;

/**
 * Comment or field update for an existing issue.
 */
public record IssueUpdate(
        String comment,
        String newStatus  // null = comment only, non-null = also transition
) {
    public static IssueUpdate comment(String text) {
        return new IssueUpdate(text, null);
    }
}
