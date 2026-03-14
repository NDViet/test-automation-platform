package com.platform.analytics.alerts;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.repository.FlakinessScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates configured alert rules against an incoming {@link UnifiedTestResult}
 * and fires {@link AlertEvent}s for any breached rules.
 *
 * <p>Use {@link #evaluate(UnifiedTestResult, UUID)} when a project UUID is available
 * (e.g. from the Kafka consumer) to also evaluate DB-backed rules such as
 * {@link AlertRule.Metric#CRITICAL_FLAKY_COUNT_ABOVE} and persist alert history.</p>
 */
@Service
public class AlertRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleEngine.class);

    private final List<AlertRule> rules;
    private final AlertNotificationService notificationService;
    private final FlakinessScoreRepository scoreRepo;
    private final AlertHistoryService historyService;

    public AlertRuleEngine(AlertProperties properties,
                           AlertNotificationService notificationService,
                           FlakinessScoreRepository scoreRepo,
                           AlertHistoryService historyService) {
        this.rules               = properties.getRules();
        this.notificationService = notificationService;
        this.scoreRepo           = scoreRepo;
        this.historyService      = historyService;
    }

    /**
     * Evaluates rules that do not require a project UUID (pass-rate, failure count).
     * DB-backed rules (CRITICAL_FLAKY_COUNT_ABOVE) are skipped.
     */
    public List<AlertEvent> evaluate(UnifiedTestResult result) {
        return evaluate(result, null);
    }

    /**
     * Evaluates all enabled rules including DB-backed ones, persists history,
     * and dispatches notifications.
     *
     * @param projectId resolved project UUID (may be null to skip DB-backed rules)
     */
    public List<AlertEvent> evaluate(UnifiedTestResult result, UUID projectId) {
        List<AlertEvent> fired = new ArrayList<>();

        for (AlertRule rule : rules) {
            if (!rule.enabled()) continue;

            AlertEvent event = switch (rule.metric()) {
                case PASS_RATE_BELOW            -> evaluatePassRate(rule, result);
                case NEW_FAILURES_ABOVE         -> evaluateNewFailures(rule, result);
                case BROKEN_TESTS_ABOVE         -> evaluateBrokenTests(rule, result);
                case CRITICAL_FLAKY_COUNT_ABOVE -> evaluateCriticalFlaky(rule, result, projectId);
            };

            if (event != null) {
                fired.add(event);
                String channels = notificationService.send(event);
                historyService.record(event, !channels.isEmpty(), channels);
            }
        }

        if (!fired.isEmpty()) {
            log.info("[Alerts] Fired {} alert(s) for run={} project={}",
                    fired.size(), result.runId(), result.projectId());
        }
        return fired;
    }

    private AlertEvent evaluatePassRate(AlertRule rule, UnifiedTestResult result) {
        if (result.total() == 0) return null;
        double passRate = (double) result.passed() / result.total();
        if (passRate < rule.threshold()) {
            return AlertEvent.of(rule,
                    String.format("Pass rate %.1f%% breached threshold %.1f%% in project=%s branch=%s",
                            passRate * 100, rule.threshold() * 100,
                            result.projectId(), result.branch()),
                    result.teamId(), result.projectId(), result.runId());
        }
        return null;
    }

    private AlertEvent evaluateNewFailures(AlertRule rule, UnifiedTestResult result) {
        long failures = result.testCases().stream()
                .filter(tc -> tc.status() == TestStatus.FAILED)
                .count();
        if (failures > rule.threshold()) {
            return AlertEvent.of(rule,
                    String.format("%d new failures exceeded threshold %.0f in project=%s branch=%s",
                            failures, rule.threshold(), result.projectId(), result.branch()),
                    result.teamId(), result.projectId(), result.runId());
        }
        return null;
    }

    private AlertEvent evaluateBrokenTests(AlertRule rule, UnifiedTestResult result) {
        long broken = result.testCases().stream()
                .filter(tc -> tc.status() == TestStatus.BROKEN)
                .count();
        if (broken > rule.threshold()) {
            return AlertEvent.of(rule,
                    String.format("%d broken tests exceeded threshold %.0f in project=%s",
                            broken, rule.threshold(), result.projectId()),
                    result.teamId(), result.projectId(), result.runId());
        }
        return null;
    }

    private AlertEvent evaluateCriticalFlaky(AlertRule rule, UnifiedTestResult result, UUID projectId) {
        if (projectId == null) return null; // requires DB access — skip if no project UUID
        long criticalCount = scoreRepo
                .findTopFlakyByProject(projectId, PageRequest.of(0, 10_000))
                .stream()
                .filter(s -> s.getClassification() == FlakinessScore.Classification.CRITICAL_FLAKY)
                .count();
        if (criticalCount > rule.threshold()) {
            return AlertEvent.of(rule,
                    String.format("%d CRITICAL_FLAKY tests exceeded threshold %.0f in project=%s",
                            criticalCount, rule.threshold(), result.projectId()),
                    result.teamId(), result.projectId(), result.runId());
        }
        return null;
    }
}
