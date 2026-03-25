package com.platform.analytics.alerts;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.core.repository.FlakinessScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertRuleEngineTest {

    @Mock AlertNotificationService notificationService;
    @Mock FlakinessScoreRepository scoreRepo;
    @Mock AlertHistoryService historyService;

    AlertRuleEngine engine;

    @BeforeEach
    void setUp() {
        AlertProperties props = new AlertProperties();
        props.setRules(List.of(
                new AlertRule("High Failure Rate", AlertRule.Metric.PASS_RATE_BELOW,
                        0.80, AlertRule.Severity.HIGH, true),
                new AlertRule("Failure Spike", AlertRule.Metric.NEW_FAILURES_ABOVE,
                        5, AlertRule.Severity.MEDIUM, true),
                new AlertRule("Broken Tests", AlertRule.Metric.BROKEN_TESTS_ABOVE,
                        3, AlertRule.Severity.HIGH, true),
                new AlertRule("Disabled Rule", AlertRule.Metric.PASS_RATE_BELOW,
                        0.99, AlertRule.Severity.LOW, false)
        ));
        engine = new AlertRuleEngine(props, notificationService, scoreRepo, historyService);
        // send() returns which channels were used; empty string = no channels configured
        lenient().when(notificationService.send(any())).thenReturn("");
    }

    @Test
    void noAlertsWhenTestsAllPass() {
        UnifiedTestResult result = buildResult(10, 10, 0, 0, List.of());
        List<AlertEvent> events = engine.evaluate(result);
        assertThat(events).isEmpty();
        verify(notificationService, never()).send(any());
    }

    @Test
    void firesPassRateAlertWhenBelowThreshold() {
        // 6 passed, 4 failed = 60% < 80%, failures below spike threshold (4 < 5)
        List<TestCaseResultDto> cases = failures(4);
        UnifiedTestResult result = buildResult(10, 6, 4, 0, cases);
        List<AlertEvent> events = engine.evaluate(result);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).ruleName()).isEqualTo("High Failure Rate");
    }

    @Test
    void firesNewFailuresAlertWhenAboveThreshold() {
        List<TestCaseResultDto> cases = failures(6); // > 5
        UnifiedTestResult result = buildResult(100, 94, 6, 0, cases);
        List<AlertEvent> events = engine.evaluate(result);
        assertThat(events.stream().anyMatch(e -> e.ruleName().equals("Failure Spike"))).isTrue();
    }

    @Test
    void firesBrokenTestsAlert() {
        List<TestCaseResultDto> cases = broken(4); // > 3
        UnifiedTestResult result = buildResult(10, 6, 0, 4, cases);
        List<AlertEvent> events = engine.evaluate(result);
        assertThat(events.stream().anyMatch(e -> e.ruleName().equals("Broken Tests"))).isTrue();
    }

    @Test
    void disabledRuleNeverFires() {
        // Even though pass rate is 0%, the disabled rule should not fire
        UnifiedTestResult result = buildResult(10, 0, 10, 0, failures(10));
        List<AlertEvent> events = engine.evaluate(result);
        assertThat(events.stream().noneMatch(e -> e.ruleName().equals("Disabled Rule"))).isTrue();
    }

    @Test
    void alertEventContainsCorrectMetadata() {
        List<TestCaseResultDto> cases = failures(6);
        UnifiedTestResult result = buildResult(10, 4, 6, 0, cases);
        List<AlertEvent> events = engine.evaluate(result);
        AlertEvent failureSpike = events.stream()
                .filter(e -> e.ruleName().equals("Failure Spike"))
                .findFirst().orElseThrow();

        assertThat(failureSpike.severity()).isEqualTo(AlertRule.Severity.MEDIUM);
        assertThat(failureSpike.teamId()).isEqualTo("team-a");
        assertThat(failureSpike.projectId()).isEqualTo("project-x");
        assertThat(failureSpike.firedAt()).isNotNull();
    }

    @Test
    void notificationIsSentForEachFiredAlert() {
        List<TestCaseResultDto> cases = failures(10); // triggers failure spike
        UnifiedTestResult result = buildResult(100, 90, 10, 0, cases);
        engine.evaluate(result);
        verify(notificationService, atLeastOnce()).send(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UnifiedTestResult buildResult(int total, int passed, int failed, int broken,
                                           List<TestCaseResultDto> cases) {
        return new UnifiedTestResult(
                "run-" + UUID.randomUUID(), "team-a", "project-x",
                "main", "staging", null, null, null, null,
                Instant.now(), total, passed, failed, 0, broken,
                null, SourceFormat.JUNIT_XML, cases,
                "UNKNOWN", 0, "",
                null, null
        );
    }

    private List<TestCaseResultDto> failures(int n) {
        List<TestCaseResultDto> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(TestCaseResultDto.basic(
                    "Test#method" + i, "method" + i, "Test", "method",
                    List.of(), TestStatus.FAILED, 100L,
                    "assertion failed", null, 0, List.of()
            ));
        }
        return list;
    }

    private List<TestCaseResultDto> broken(int n) {
        List<TestCaseResultDto> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(TestCaseResultDto.basic(
                    "Test#broken" + i, "broken" + i, "Test", "broken",
                    List.of(), TestStatus.BROKEN, 100L,
                    "unexpected status", null, 0, List.of()
            ));
        }
        return list;
    }
}
