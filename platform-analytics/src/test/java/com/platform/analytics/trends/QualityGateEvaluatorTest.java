package com.platform.analytics.trends;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.core.repository.TestExecutionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityGateEvaluatorTest {

    @Mock TestExecutionRepository executionRepo;

    QualityGateEvaluator evaluator;
    UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new QualityGateEvaluator(0.80, 10, executionRepo, new SimpleMeterRegistry());
        when(executionRepo.computePassRate(any(), any())).thenReturn(null); // no history
    }

    @Test
    void passesWith100PercentPassRate() {
        UnifiedTestResult result = buildResult(10, 10, 0, 0, List.of());
        QualityGateResult gate = evaluator.evaluate(result, projectId);
        assertThat(gate.passed()).isTrue();
        assertThat(gate.violations()).isEmpty();
    }

    @Test
    void passesAt80PercentExactly() {
        // 8 passed, 2 failed = 80% → meets threshold exactly
        List<TestCaseResultDto> cases = List.of(
                testCase(TestStatus.FAILED),
                testCase(TestStatus.FAILED)
        );
        UnifiedTestResult result = buildResult(10, 8, 2, 0, cases);
        QualityGateResult gate = evaluator.evaluate(result, projectId);
        assertThat(gate.passed()).isTrue();
    }

    @Test
    void failsWhenPassRateBelowThreshold() {
        // 7 passed, 3 failed = 70% < 80%
        List<TestCaseResultDto> cases = List.of(
                testCase(TestStatus.FAILED),
                testCase(TestStatus.FAILED),
                testCase(TestStatus.FAILED)
        );
        UnifiedTestResult result = buildResult(10, 7, 3, 0, cases);
        QualityGateResult gate = evaluator.evaluate(result, projectId);
        assertThat(gate.passed()).isFalse();
        assertThat(gate.violations()).hasSize(1);
        assertThat(gate.violations().get(0)).contains("below minimum");
    }

    @Test
    void failsWhenTooManyNewFailures() {
        // 11 failures > max of 10
        List<TestCaseResultDto> cases = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) cases.add(testCase(TestStatus.FAILED));
        // 89 total so pass rate is still 70% which also triggers pass rate gate
        UnifiedTestResult result = buildResult(100, 89, 11, 0, cases);
        QualityGateResult gate = evaluator.evaluate(result, projectId);
        assertThat(gate.passed()).isFalse();
        assertThat(gate.violations().stream().anyMatch(v -> v.contains("failure count")))
                .isTrue();
    }

    @Test
    void failsWhenPassRateDropsMoreThan10PercentBelowRollingAverage() {
        // Rolling average is 95%, current is 83% (12%+ drop)
        when(executionRepo.computePassRate(any(), any())).thenReturn(0.95);

        List<TestCaseResultDto> cases = List.of(testCase(TestStatus.FAILED));
        UnifiedTestResult result = buildResult(6, 5, 1, 0, cases); // 83%
        QualityGateResult gate = evaluator.evaluate(result, projectId);
        assertThat(gate.passed()).isFalse();
        assertThat(gate.violations().stream().anyMatch(v -> v.contains("7-day average"))).isTrue();
    }

    @Test
    void passesWhenPassRateDropsLessThan10PercentBelowRollingAverage() {
        // Rolling average is 90%, current is 82% (8.8% drop, under 10%)
        when(executionRepo.computePassRate(any(), any())).thenReturn(0.90);

        List<TestCaseResultDto> cases = List.of(testCase(TestStatus.FAILED));
        UnifiedTestResult result = buildResult(11, 9, 1, 1, cases); // ~82%
        QualityGateResult gate = evaluator.evaluate(result, projectId);
        // 0.82 / 0.90 = 0.91 > 0.90 threshold → should pass trend check
        assertThat(gate.violations().stream().anyMatch(v -> v.contains("7-day average"))).isFalse();
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

    private TestCaseResultDto testCase(TestStatus status) {
        return TestCaseResultDto.basic(
                "com.example.Test#method", "method", "Test", "method",
                List.of(), status, 100L, null, null, 0, List.of()
        );
    }
}
