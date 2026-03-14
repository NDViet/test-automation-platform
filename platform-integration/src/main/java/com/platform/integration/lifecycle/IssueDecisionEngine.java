package com.platform.integration.lifecycle;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.IssueTrackerLink;
import com.platform.core.domain.TestCaseResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Pure decision logic — no DB access. All data is injected via {@link DecisionInput}.
 *
 * <h3>Rules (evaluated in order):</h3>
 * <ol>
 *   <li><b>Close</b>: test passed ≥ N consecutive runs AND open ticket exists</li>
 *   <li><b>Reopen</b>: test failing AND closed/done ticket exists</li>
 *   <li><b>Update</b>: test failing AND open ticket exists → add occurrence comment</li>
 *   <li><b>Skip</b>: test not currently failing AND no open ticket</li>
 *   <li><b>Create TEST_MAINTENANCE</b>: flakiness score > threshold AND no open ticket</li>
 *   <li><b>Create BUG</b>: consecutive failures ≥ min threshold AND no open ticket</li>
 *   <li><b>Skip</b>: not enough signal yet</li>
 * </ol>
 */
@Component
public class IssueDecisionEngine {

    /**
     * All data needed to make a decision — assembled by {@link TicketLifecycleManager}.
     */
    public record DecisionInput(
            String testId,
            List<TestCaseResult> recentHistory,    // sorted DESC by createdAt
            FlakinessScore flakinessScore,          // may be null
            Optional<IssueTrackerLink> existingLink,
            int minConsecutiveFailures,
            double flakinessThreshold,
            int minConsecutivePasses                // passes needed to close ticket
    ) {}

    public IssueDecision decide(DecisionInput in) {
        boolean isCurrentlyFailing = isCurrentlyFailing(in.recentHistory());
        int consecutiveFails  = countConsecutive(in.recentHistory(), true);
        int consecutivePasses = countConsecutive(in.recentHistory(), false);
        boolean hasOpenTicket = in.existingLink()
                .map(l -> !isDoneOrClosed(l.getIssueStatus()))
                .orElse(false);
        boolean hasClosedTicket = in.existingLink()
                .map(l -> isDoneOrClosed(l.getIssueStatus()))
                .orElse(false);

        // Rule 1 — Close
        if (consecutivePasses >= in.minConsecutivePasses() && hasOpenTicket) {
            return IssueDecision.close(
                    "Test passed in last " + consecutivePasses + " consecutive runs. "
                    + "Automatically closing.");
        }

        // Rule 2 — Reopen
        if (isCurrentlyFailing && hasClosedTicket) {
            return IssueDecision.reopen(
                    "Regression detected — test is failing again after " + consecutiveFails
                    + " consecutive failure(s).");
        }

        // Rule 3 — Update open ticket
        if (isCurrentlyFailing && hasOpenTicket) {
            return IssueDecision.update(
                    "Test still failing — " + consecutiveFails + " consecutive failure(s). "
                    + "Keeping ticket open.");
        }

        // Rule 4 — Not failing and no ticket
        if (!isCurrentlyFailing) {
            return IssueDecision.skip("Test is not currently failing.");
        }

        // Rules 5 & 6 — possibly create a ticket

        // Rule 5 — Create TEST_MAINTENANCE for flaky tests
        if (in.flakinessScore() != null
                && in.flakinessScore().getScore().doubleValue() > in.flakinessThreshold()) {
            return IssueDecision.create(
                    IssueDecision.IssueType.TEST_MAINTENANCE,
                    "Flakiness score " + in.flakinessScore().getScore()
                    + " exceeds threshold " + in.flakinessThreshold()
                    + " (classification=" + in.flakinessScore().getClassification() + ").");
        }

        // Rule 6 — Create BUG after N consecutive failures
        if (consecutiveFails >= in.minConsecutiveFailures()) {
            return IssueDecision.create(
                    IssueDecision.IssueType.BUG,
                    "Test failed " + consecutiveFails + " consecutive times "
                    + "(threshold=" + in.minConsecutiveFailures() + ").");
        }

        // Rule 7 — Not enough signal
        return IssueDecision.skip(
                "Only " + consecutiveFails + "/" + in.minConsecutiveFailures()
                + " consecutive failures — threshold not met.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isCurrentlyFailing(List<TestCaseResult> history) {
        if (history.isEmpty()) return false;
        TestStatus s = history.get(0).getStatus();
        return s == TestStatus.FAILED || s == TestStatus.BROKEN;
    }

    /**
     * Counts consecutive runs from the head of the history list.
     *
     * @param lookingForFailures {@code true} = count failures, {@code false} = count passes
     */
    int countConsecutive(List<TestCaseResult> history, boolean lookingForFailures) {
        int count = 0;
        for (TestCaseResult r : history) {
            boolean isFail = r.getStatus() == TestStatus.FAILED
                    || r.getStatus() == TestStatus.BROKEN;
            if (lookingForFailures ? isFail : !isFail) count++;
            else break;
        }
        return count;
    }

    private boolean isDoneOrClosed(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        return s.contains("done") || s.contains("closed") || s.contains("resolved");
    }
}
