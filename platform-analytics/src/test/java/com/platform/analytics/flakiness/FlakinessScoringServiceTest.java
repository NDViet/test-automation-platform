package com.platform.analytics.flakiness;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.TestCaseResultRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlakinessScoringServiceTest {

    @Mock TestCaseResultRepository resultRepo;
    @Mock FlakinessScoreRepository scoreRepo;

    FlakinessScoringService service;

    @BeforeEach
    void setUp() {
        lenient().when(scoreRepo.countByClassification(any())).thenReturn(0L);
        service = new FlakinessScoringService(resultRepo, scoreRepo, new SimpleMeterRegistry());
    }

    // ── classify ─────────────────────────────────────────────────────────────

    @Test
    void classifyStable() {
        assertThat(FlakinessScoringService.classify(0.05)).isEqualTo(FlakinessScore.Classification.STABLE);
        assertThat(FlakinessScoringService.classify(0.0)).isEqualTo(FlakinessScore.Classification.STABLE);
    }

    @Test
    void classifyWatch() {
        assertThat(FlakinessScoringService.classify(0.10)).isEqualTo(FlakinessScore.Classification.WATCH);
        assertThat(FlakinessScoringService.classify(0.25)).isEqualTo(FlakinessScore.Classification.WATCH);
    }

    @Test
    void classifyFlaky() {
        assertThat(FlakinessScoringService.classify(0.30)).isEqualTo(FlakinessScore.Classification.FLAKY);
        assertThat(FlakinessScoringService.classify(0.50)).isEqualTo(FlakinessScoringService.classify(0.50));
    }

    @Test
    void classifyCriticalFlaky() {
        assertThat(FlakinessScoringService.classify(0.60)).isEqualTo(FlakinessScore.Classification.CRITICAL_FLAKY);
        assertThat(FlakinessScoringService.classify(1.0)).isEqualTo(FlakinessScore.Classification.CRITICAL_FLAKY);
    }

    // ── buildInput ────────────────────────────────────────────────────────────

    @Test
    void failureRateIsComputedCorrectly() {
        // 3 passed, 2 failed out of 5 total
        List<TestCaseResult> history = List.of(
                result(TestStatus.PASSED, "env1", daysAgo(1)),
                result(TestStatus.PASSED, "env1", daysAgo(2)),
                result(TestStatus.PASSED, "env1", daysAgo(3)),
                result(TestStatus.FAILED, "env1", daysAgo(4)),
                result(TestStatus.FAILED, "env1", daysAgo(5))
        );

        FlakinessScoringService.ScoringInput input = service.buildInput(history);

        assertThat(input.total()).isEqualTo(5);
        assertThat(input.failures()).isEqualTo(2);
        assertThat(input.failureRate()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void envVarianceIsZeroWithSingleEnvironment() {
        List<TestCaseResult> history = List.of(
                result(TestStatus.FAILED, "staging", daysAgo(1)),
                result(TestStatus.PASSED, "staging", daysAgo(2)),
                result(TestStatus.FAILED, "staging", daysAgo(3))
        );

        FlakinessScoringService.ScoringInput input = service.buildInput(history);

        assertThat(input.envVariance()).isEqualTo(0.0);
    }

    @Test
    void envVarianceIsHighWhenFailsInOneEnvOnly() {
        // Always fails in staging, always passes in production
        List<TestCaseResult> history = List.of(
                result(TestStatus.FAILED, "staging", daysAgo(1)),
                result(TestStatus.FAILED, "staging", daysAgo(2)),
                result(TestStatus.PASSED, "production", daysAgo(1)),
                result(TestStatus.PASSED, "production", daysAgo(2))
        );

        FlakinessScoringService.ScoringInput input = service.buildInput(history);

        // staging failure rate = 1.0, production failure rate = 0.0 → stddev = 0.5
        assertThat(input.envVariance()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void recencyWeightHigherWhenRecentFailures() {
        // Recent failures should produce higher recency weight than older ones
        List<TestCaseResult> recentFailures = List.of(
                result(TestStatus.FAILED, "env", daysAgo(0)),
                result(TestStatus.FAILED, "env", daysAgo(1)),
                result(TestStatus.PASSED, "env", daysAgo(28)),
                result(TestStatus.PASSED, "env", daysAgo(29))
        );
        List<TestCaseResult> oldFailures = List.of(
                result(TestStatus.PASSED, "env", daysAgo(0)),
                result(TestStatus.PASSED, "env", daysAgo(1)),
                result(TestStatus.FAILED, "env", daysAgo(28)),
                result(TestStatus.FAILED, "env", daysAgo(29))
        );

        FlakinessScoringService.ScoringInput recent = service.buildInput(recentFailures);
        FlakinessScoringService.ScoringInput old    = service.buildInput(oldFailures);

        assertThat(recent.recencyWeight()).isGreaterThan(old.recencyWeight());
    }

    // ── computeAndPersist ─────────────────────────────────────────────────────

    @Test
    void computeAndPersistSkipsWhenNoHistory() {
        UUID projectId = UUID.randomUUID();
        when(resultRepo.findWithExecutionByTestIdAndProjectIdSince(any(), any(), any()))
                .thenReturn(List.of());

        service.computeAndPersist("com.example.Test#method", projectId);

        verify(scoreRepo, never()).upsert(any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void computeAndPersistCallsUpsertWithCorrectClassification() {
        UUID projectId = UUID.randomUUID();
        // All failures → high score → CRITICAL_FLAKY
        List<TestCaseResult> allFailed = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            allFailed.add(result(TestStatus.FAILED, "env", daysAgo(i)));
        }
        when(resultRepo.findWithExecutionByTestIdAndProjectIdSince(any(), any(), any()))
                .thenReturn(allFailed);

        service.computeAndPersist("com.example.Test#flaky", projectId);

        ArgumentCaptor<String> classCaptor = ArgumentCaptor.forClass(String.class);
        verify(scoreRepo).upsert(any(), any(), any(), classCaptor.capture(),
                anyInt(), anyInt(), any(), any(), any());
        assertThat(classCaptor.getValue()).isEqualTo("CRITICAL_FLAKY");
    }

    @Test
    void computeAndPersistSetsStableForAlwaysPassingTest() {
        UUID projectId = UUID.randomUUID();
        List<TestCaseResult> allPassed = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            allPassed.add(result(TestStatus.PASSED, "env", daysAgo(i)));
        }
        when(resultRepo.findWithExecutionByTestIdAndProjectIdSince(any(), any(), any()))
                .thenReturn(allPassed);

        service.computeAndPersist("com.example.Test#stable", projectId);

        ArgumentCaptor<String> classCaptor = ArgumentCaptor.forClass(String.class);
        verify(scoreRepo).upsert(any(), any(), any(), classCaptor.capture(),
                anyInt(), anyInt(), any(), any(), any());
        assertThat(classCaptor.getValue()).isEqualTo("STABLE");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TestCaseResult result(TestStatus status, String environment, Instant createdAt) {
        try {
            TestExecution execution = new TestExecution.Builder()
                    .environment(environment)
                    .runId("run-" + UUID.randomUUID())
                    .totalTests(1).passed(status == TestStatus.PASSED ? 1 : 0)
                    .failed(status == TestStatus.FAILED ? 1 : 0)
                    .broken(status == TestStatus.BROKEN ? 1 : 0)
                    .skipped(0)
                    .executedAt(createdAt)
                    .build();

            TestCaseResult tcr = TestCaseResult.builder()
                    .execution(execution)
                    .testId("com.example.Test#method")
                    .status(status)
                    .build();

            // Force createdAt via reflection since it's set by auditing
            Field createdAtField = TestCaseResult.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(tcr, createdAt);
            return tcr;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
