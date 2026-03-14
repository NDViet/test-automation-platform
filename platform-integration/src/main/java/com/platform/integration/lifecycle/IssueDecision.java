package com.platform.integration.lifecycle;

/**
 * Output of the {@link IssueDecisionEngine} — what action to take on the issue tracker.
 */
public record IssueDecision(
        Action action,
        IssueType issueType,
        String reason
) {
    public enum Action { CREATE, UPDATE, CLOSE, REOPEN, SKIP }

    public enum IssueType {
        BUG,              // Application defect
        TEST_MAINTENANCE, // Flaky test needs fixing
        TEST_FIX          // Test code defect
    }

    public static IssueDecision create(IssueType type, String reason) {
        return new IssueDecision(Action.CREATE, type, reason);
    }

    public static IssueDecision update(String reason) {
        return new IssueDecision(Action.UPDATE, null, reason);
    }

    public static IssueDecision close(String reason) {
        return new IssueDecision(Action.CLOSE, null, reason);
    }

    public static IssueDecision reopen(String reason) {
        return new IssueDecision(Action.REOPEN, null, reason);
    }

    public static IssueDecision skip(String reason) {
        return new IssueDecision(Action.SKIP, null, reason);
    }
}
