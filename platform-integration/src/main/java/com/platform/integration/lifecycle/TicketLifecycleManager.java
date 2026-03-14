package com.platform.integration.lifecycle;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.IntegrationConfig;
import com.platform.core.domain.IssueTrackerLink;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.integration.port.IssueReference;
import com.platform.integration.port.IssueRequest;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.IssueUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the full ticket lifecycle for a test run.
 *
 * <p>For each test case result in an incoming run:</p>
 * <ol>
 *   <li>Loads recent history and flakiness score from DB</li>
 *   <li>Delegates the decision to {@link IssueDecisionEngine}</li>
 *   <li>Calls the tracker to create/update/close/reopen</li>
 *   <li>Persists or updates the {@link IssueTrackerLink}</li>
 * </ol>
 */
@Service
public class TicketLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(TicketLifecycleManager.class);

    static final int HISTORY_DAYS = 30;
    static final int DEFAULT_MIN_CONSECUTIVE_FAILURES = 3;
    static final int DEFAULT_MIN_CONSECUTIVE_PASSES   = 3;
    static final double DEFAULT_FLAKINESS_THRESHOLD   = 0.30;

    private final TestCaseResultRepository resultRepo;
    private final FlakinessScoreRepository scoreRepo;
    private final IssueDecisionEngine decisionEngine;
    private final DuplicateDetector duplicateDetector;

    public TicketLifecycleManager(TestCaseResultRepository resultRepo,
                                  FlakinessScoreRepository scoreRepo,
                                  IssueDecisionEngine decisionEngine,
                                  DuplicateDetector duplicateDetector) {
        this.resultRepo       = resultRepo;
        this.scoreRepo        = scoreRepo;
        this.decisionEngine   = decisionEngine;
        this.duplicateDetector = duplicateDetector;
    }

    /**
     * Processes all test results in a run. Handles failing tests plus tests
     * with existing tickets (for close/reopen).
     */
    @Transactional
    public void processRun(UnifiedTestResult result, UUID projectId,
                           IntegrationConfig config, IssueTrackerPort tracker) {
        // Collect test IDs to process: currently failing + already linked
        Set<String> failingIds = result.testCases().stream()
                .filter(tc -> tc.status() == TestStatus.FAILED || tc.status() == TestStatus.BROKEN)
                .map(TestCaseResultDto::testId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Set<String> linkedIds = new HashSet<>(
                duplicateDetector.findLinkedTestIds(projectId, tracker.trackerType()));

        Set<String> toProcess = new HashSet<>(failingIds);
        toProcess.addAll(linkedIds);

        if (toProcess.isEmpty()) {
            log.debug("[Integration] No tests to process for run={}", result.runId());
            return;
        }

        log.info("[Integration] Processing {} test(s) for run={} tracker={}",
                toProcess.size(), result.runId(), tracker.trackerType());

        int configMinFails   = parseInt(config.config("minConsecutiveFailures"), DEFAULT_MIN_CONSECUTIVE_FAILURES);
        int configMinPasses  = parseInt(config.config("minConsecutivePasses"),   DEFAULT_MIN_CONSECUTIVE_PASSES);
        double flakyThreshold = parseDouble(config.config("flakinessThreshold"), DEFAULT_FLAKINESS_THRESHOLD);

        for (String testId : toProcess) {
            try {
                processTest(testId, projectId, result, config, tracker,
                        configMinFails, configMinPasses, flakyThreshold);
            } catch (Exception e) {
                log.warn("[Integration] Failed to process testId={}: {}", testId, e.getMessage());
            }
        }
    }

    private void processTest(String testId, UUID projectId, UnifiedTestResult result,
                              IntegrationConfig config, IssueTrackerPort tracker,
                              int minFails, int minPasses, double flakyThreshold) {

        Instant since = Instant.now().minus(HISTORY_DAYS, ChronoUnit.DAYS);
        List<TestCaseResult> history =
                resultRepo.findWithExecutionByTestIdAndProjectIdSince(testId, projectId, since);

        FlakinessScore flakinessScore = scoreRepo.findByTestIdAndProjectId(testId, projectId)
                .orElse(null);

        Optional<IssueTrackerLink> existingLink =
                duplicateDetector.findExisting(testId, projectId, tracker.trackerType());

        IssueDecisionEngine.DecisionInput input = new IssueDecisionEngine.DecisionInput(
                testId, history, flakinessScore, existingLink,
                minFails, flakyThreshold, minPasses);

        IssueDecision decision = decisionEngine.decide(input);
        log.debug("[Integration] testId={} action={} reason={}", testId, decision.action(), decision.reason());

        executeDecision(decision, testId, projectId, result, config, tracker, existingLink);
    }

    private void executeDecision(IssueDecision decision, String testId, UUID projectId,
                                  UnifiedTestResult result, IntegrationConfig config,
                                  IssueTrackerPort tracker, Optional<IssueTrackerLink> existingLink) {
        switch (decision.action()) {
            case CREATE -> {
                String title = buildTitle(decision.issueType(), testId);
                String desc  = buildDescription(testId, result, decision.reason());
                String priority = "main".equals(result.branch()) ? "High" : "Medium";
                String jiraType = decision.issueType() == IssueDecision.IssueType.BUG ? "Bug" : "Task";

                IssueRequest req = new IssueRequest(
                        title, desc, jiraType, priority,
                        config.getProjectKey(),
                        List.of(sanitizeLabel(testId), "platform-auto"),
                        testId, result.teamId());

                IssueReference ref = tracker.createIssue(req);
                duplicateDetector.saveOrUpdate(testId, projectId, tracker.trackerType(),
                        ref.key(), ref.url(), jiraType, "Open");
                log.info("[Integration] Created {} {} for test={}", jiraType, ref.key(), testId);
            }
            case UPDATE -> existingLink.ifPresent(link -> {
                String comment = buildUpdateComment(testId, result, decision.reason());
                tracker.updateIssue(link.getIssueKey(), IssueUpdate.comment(comment));
                duplicateDetector.saveOrUpdate(testId, projectId, tracker.trackerType(),
                        link.getIssueKey(), link.getIssueUrl(), link.getIssueType(), "Open");
            });
            case CLOSE -> existingLink.ifPresent(link -> {
                String comment = "Test passed in the last consecutive runs. "
                        + "Auto-closing. Run: " + result.runId();
                tracker.closeIssue(link.getIssueKey(), comment);
                duplicateDetector.saveOrUpdate(testId, projectId, tracker.trackerType(),
                        link.getIssueKey(), link.getIssueUrl(), link.getIssueType(), "Done");
                log.info("[Integration] Closed {} for test={}", link.getIssueKey(), testId);
            });
            case REOPEN -> existingLink.ifPresent(link -> {
                String comment = "Regression detected in run " + result.runId()
                        + ". " + decision.reason();
                tracker.reopenIssue(link.getIssueKey(), comment);
                duplicateDetector.saveOrUpdate(testId, projectId, tracker.trackerType(),
                        link.getIssueKey(), link.getIssueUrl(), link.getIssueType(), "Open");
                log.info("[Integration] Reopened {} for test={}", link.getIssueKey(), testId);
            });
            case SKIP -> log.debug("[Integration] Skipping test={}: {}", testId, decision.reason());
        }
    }

    // ── Formatting helpers ─────────────────────────────────────────────────────

    private String buildTitle(IssueDecision.IssueType type, String testId) {
        String shortName = testId.contains("#") ? testId.substring(testId.lastIndexOf('#') + 1) : testId;
        if (shortName.length() > 60) shortName = shortName.substring(0, 60) + "...";
        return switch (type) {
            case BUG              -> "[Auto] Test failure: " + shortName;
            case TEST_MAINTENANCE -> "[Auto] Flaky test: " + shortName;
            case TEST_FIX         -> "[Auto] Test defect: " + shortName;
        };
    }

    private String buildDescription(String testId, UnifiedTestResult result, String reason) {
        return "**Automatically created by Test Automation Platform**\n\n"
                + "**Test:** `" + testId + "`\n"
                + "**Project:** " + result.projectId() + " | **Team:** " + result.teamId() + "\n"
                + "**Branch:** " + result.branch() + " | **Environment:** " + result.environment() + "\n"
                + "**Run ID:** " + result.runId() + "\n\n"
                + "**Reason:** " + reason + "\n\n"
                + (result.ciRunUrl() != null ? "**CI Run:** " + result.ciRunUrl() : "");
    }

    private String buildUpdateComment(String testId, UnifiedTestResult result, String reason) {
        return "🔁 **Test still failing** — " + reason + "\n"
                + "Run: " + result.runId() + " | Branch: " + result.branch()
                + " | " + result.environment();
    }

    private String sanitizeLabel(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private int parseInt(String val, int fallback) {
        if (val == null) return fallback;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return fallback; }
    }

    private double parseDouble(String val, double fallback) {
        if (val == null) return fallback;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return fallback; }
    }
}
