package com.platform.integration.lifecycle;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.IssueTrackerLink;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IssueDecisionEngineTest {

    IssueDecisionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new IssueDecisionEngine();
    }

    // ── Rule 1: Close ─────────────────────────────────────────────────────────

    @Test
    void closesTicketAfterMinConsecutivePasses() {
        List<TestCaseResult> history = history(TestStatus.PASSED, 3);
        IssueTrackerLink openLink = link("JIRA", "Open");

        IssueDecision decision = engine.decide(input(history, null, Optional.of(openLink), 3, 3));

        assertThat(decision.action()).isEqualTo(IssueDecision.Action.CLOSE);
        assertThat(decision.reason()).contains("consecutive runs");
    }

    @Test
    void doesNotCloseWhenNotEnoughPasses() {
        List<TestCaseResult> history = history(TestStatus.PASSED, 2);
        IssueTrackerLink openLink = link("JIRA", "Open");

        IssueDecision decision = engine.decide(input(history, null, Optional.of(openLink), 3, 3));

        // Should not close (only 2 passes < min 3); might skip since not failing
        assertThat(decision.action()).isNotEqualTo(IssueDecision.Action.CLOSE);
    }

    @Test
    void doesNotCloseAlreadyDoneTicket() {
        List<TestCaseResult> history = history(TestStatus.PASSED, 5);
        IssueTrackerLink doneLink = link("JIRA", "Done");

        IssueDecision decision = engine.decide(input(history, null, Optional.of(doneLink), 3, 3));

        // Already done — no reopen (test is passing), should skip
        assertThat(decision.action()).isEqualTo(IssueDecision.Action.SKIP);
    }

    // ── Rule 2: Reopen ────────────────────────────────────────────────────────

    @Test
    void reopensClosedTicketOnRegression() {
        List<TestCaseResult> history = history(TestStatus.FAILED, 1);
        IssueTrackerLink doneLink = link("JIRA", "Done");

        IssueDecision decision = engine.decide(input(history, null, Optional.of(doneLink), 3, 3));

        assertThat(decision.action()).isEqualTo(IssueDecision.Action.REOPEN);
        assertThat(decision.reason()).contains("Regression");
    }

    // ── Rule 3: Update ────────────────────────────────────────────────────────

    @Test
    void updatesOpenTicketWhenStillFailing() {
        List<TestCaseResult> history = history(TestStatus.FAILED, 2);
        IssueTrackerLink openLink = link("JIRA", "In Progress");

        IssueDecision decision = engine.decide(input(history, null, Optional.of(openLink), 3, 3));

        assertThat(decision.action()).isEqualTo(IssueDecision.Action.UPDATE);
    }

    // ── Rule 4: Skip (not failing, no ticket) ─────────────────────────────────

    @Test
    void skipsWhenTestIsPassingAndNoTicket() {
        List<TestCaseResult> history = history(TestStatus.PASSED, 5);

        IssueDecision decision = engine.decide(input(history, null, Optional.empty(), 3, 3));

        assertThat(decision.action()).isEqualTo(IssueDecision.Action.SKIP);
        assertThat(decision.reason()).contains("not currently failing");
    }

    // ── Rule 5: Create TEST_MAINTENANCE ───────────────────────────────────────

    @Test
    void createsTestMaintenanceForFlakyTest() {
        List<TestCaseResult> history = history(TestStatus.FAILED, 1);
        FlakinessScore score = flakinessScore(0.45, FlakinessScore.Classification.FLAKY);

        IssueDecision decision = engine.decide(input(history, score, Optional.empty(), 3, 3));

        assertThat(decision.action()).isEqualTo(IssueDecision.Action.CREATE);
        assertThat(decision.issueType()).isEqualTo(IssueDecision.IssueType.TEST_MAINTENANCE);
        assertThat(decision.reason()).contains("Flakiness score");
    }

    // ── Rule 6: Create BUG ────────────────────────────────────────────────────

    @Test
    void createsBugAfterConsecutiveFailures() {
        List<TestCaseResult> history = history(TestStatus.FAILED, 3);

        IssueDecision decision = engine.decide(input(history, null, Optional.empty(), 3, 3));

        assertThat(decision.action()).isEqualTo(IssueDecision.Action.CREATE);
        assertThat(decision.issueType()).isEqualTo(IssueDecision.IssueType.BUG);
        assertThat(decision.reason()).contains("3 consecutive");
    }

    @Test
    void skipsWhenConsecutiveFailuresBelowThreshold() {
        List<TestCaseResult> history = history(TestStatus.FAILED, 2); // < 3

        IssueDecision decision = engine.decide(input(history, null, Optional.empty(), 3, 3));

        assertThat(decision.action()).isEqualTo(IssueDecision.Action.SKIP);
        assertThat(decision.reason()).contains("2/3");
    }

    // ── countConsecutive ──────────────────────────────────────────────────────

    @Test
    void countConsecutiveStopsAtStatusChange() {
        List<TestCaseResult> history = List.of(
                tcr(TestStatus.FAILED),
                tcr(TestStatus.FAILED),
                tcr(TestStatus.PASSED), // stop here
                tcr(TestStatus.FAILED)
        );
        assertThat(engine.countConsecutive(history, true)).isEqualTo(2);
    }

    @Test
    void countConsecutiveFailuresIncludesBroken() {
        List<TestCaseResult> history = List.of(
                tcr(TestStatus.BROKEN),
                tcr(TestStatus.FAILED),
                tcr(TestStatus.PASSED)
        );
        assertThat(engine.countConsecutive(history, true)).isEqualTo(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IssueDecisionEngine.DecisionInput input(List<TestCaseResult> history,
                                                     FlakinessScore score,
                                                     Optional<IssueTrackerLink> link,
                                                     int minFails, int minPasses) {
        return new IssueDecisionEngine.DecisionInput(
                "com.example.Test#method", history, score, link, minFails, 0.30, minPasses);
    }

    private List<TestCaseResult> history(TestStatus status, int count) {
        List<TestCaseResult> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(tcr(status));
        return list;
    }

    private TestCaseResult tcr(TestStatus status) {
        return TestCaseResult.builder()
                .execution(new TestExecution.Builder()
                        .runId(UUID.randomUUID().toString())
                        .environment("staging").totalTests(1)
                        .passed(status == TestStatus.PASSED ? 1 : 0)
                        .failed(status == TestStatus.FAILED ? 1 : 0)
                        .broken(status == TestStatus.BROKEN ? 1 : 0)
                        .skipped(0).executedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                        .build())
                .testId("com.example.Test#method")
                .status(status)
                .build();
    }

    private IssueTrackerLink link(String trackerType, String status) {
        IssueTrackerLink l = new IssueTrackerLink(
                "com.example.Test#method", UUID.randomUUID(), trackerType,
                "TEST-123", "https://jira.example.com/browse/TEST-123", "Bug");
        l.syncStatus(status);
        return l;
    }

    private FlakinessScore flakinessScore(double score, FlakinessScore.Classification cls) {
        return FlakinessScore.builder()
                .testId("com.example.Test#method")
                .projectId(UUID.randomUUID())
                .score(BigDecimal.valueOf(score))
                .classification(cls)
                .totalRuns(10).failureCount(5)
                .failureRate(BigDecimal.valueOf(0.5))
                .build();
    }
}
