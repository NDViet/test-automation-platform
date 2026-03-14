package com.platform.ai.classification;

import com.platform.ai.client.AiAnalysisResponse;
import com.platform.ai.client.AiClient;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FailureAnalysis;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.FailureAnalysisRepository;
import com.platform.core.repository.TestCaseResultRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
class FailureClassificationServiceTest {

    @Mock AiClient aiClient;
    @Mock TestCaseResultRepository resultRepo;
    @Mock FailureAnalysisRepository analysisRepo;

    PromptBuilder promptBuilder = new PromptBuilder();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    FailureClassificationService service;

    UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(aiClient.providerName()).thenReturn("claude-opus-4-6");
        service = new FailureClassificationService(
                aiClient, promptBuilder, resultRepo, analysisRepo, meterRegistry);
    }

    @Test
    void classifiesFailureAndPersistsAnalysis() {
        TestCaseResult failure = buildFailure("com.example.Test#login",
                "AssertionError: expected 200 but was 500",
                "at com.example.LoginTest.login(LoginTest.java:42)");

        when(resultRepo.findLatestByTestIdAndProjectId(any(), any(), any())).thenReturn(List.of());
        when(aiClient.analyse(anyString(), anyString())).thenReturn(
                new AiAnalysisResponse(
                        new ClaudeAnalysisResult(
                                "APPLICATION_BUG", 0.9,
                                "HTTP 500 from login endpoint",
                                "The login endpoint returned 500 due to uncaught NullPointerException",
                                "Fix null check in LoginService.authenticate()",
                                false, "LoginService"),
                        350, 120));
        when(analysisRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FailureAnalysis result = service.classify(failure, projectId);

        assertThat(result.getCategory()).isEqualTo("APPLICATION_BUG");
        assertThat(result.getConfidence()).isEqualTo(0.9);
        assertThat(result.getRootCause()).contains("500");
        assertThat(result.isFlakyCandidate()).isFalse();
        assertThat(result.getModelVersion()).isEqualTo("claude-opus-4-6");
        assertThat(result.getInputTokens()).isEqualTo(350);
        assertThat(result.getOutputTokens()).isEqualTo(120);
        assertThat(result.getTotalTokens()).isEqualTo(470);

        verify(analysisRepo).save(any(FailureAnalysis.class));
    }

    @Test
    void recordsTokenMetrics() {
        TestCaseResult failure = buildFailure("test#metrics", "fail", null);

        when(resultRepo.findLatestByTestIdAndProjectId(any(), any(), any())).thenReturn(List.of());
        when(aiClient.analyse(anyString(), anyString())).thenReturn(
                new AiAnalysisResponse(
                        new ClaudeAnalysisResult("TEST_DEFECT", 0.8, "rc", "da", "sf", false, "Comp"),
                        200, 80));
        when(analysisRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.classify(failure, projectId);

        assertThat(meterRegistry.counter("ai.tokens.input", "provider", "claude-opus-4-6").count())
                .isEqualTo(200);
        assertThat(meterRegistry.counter("ai.tokens.output", "provider", "claude-opus-4-6").count())
                .isEqualTo(80);
        assertThat(meterRegistry.counter("ai.analyses.total",
                "provider", "claude-opus-4-6", "category", "TEST_DEFECT").count())
                .isEqualTo(1);
    }

    @Test
    void skipsAnalysisWhenAlreadyExists() {
        TestCaseResult failure = buildFailure("test#m", "fail", null);
        FailureAnalysis existing = FailureAnalysis.builder()
                .testId("test#m").projectId(projectId)
                .category("TEST_DEFECT").confidence(0.8)
                .build();

        when(analysisRepo.existsByTestCaseResultId(isNull())).thenReturn(true);
        when(analysisRepo.findTopByTestIdAndProjectIdOrderByAnalysedAtDesc(any(), any()))
                .thenReturn(Optional.of(existing));

        FailureAnalysis result = service.classify(failure, projectId);

        assertThat(result.getCategory()).isEqualTo("TEST_DEFECT");
        verify(aiClient, never()).analyse(anyString(), anyString());
        verify(analysisRepo, never()).save(any());
    }

    @Test
    void includesRunHistoryInPrompt() {
        TestCaseResult failure = buildFailure("test#flaky", "timeout", null);

        when(resultRepo.findLatestByTestIdAndProjectId(any(), any(), any()))
                .thenReturn(List.of(failure, failure));
        when(aiClient.analyse(anyString(), anyString())).thenReturn(
                new AiAnalysisResponse(
                        new ClaudeAnalysisResult("FLAKY_TIMING", 0.75,
                                "Async timing issue", "Detail", "Add explicit wait", true, "TestUtil"),
                        150, 60));
        when(analysisRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        service.classify(failure, projectId);

        verify(aiClient).analyse(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue()).contains("Recent");
    }

    @Test
    void setsFlakyCandidateFromAiResponse() {
        TestCaseResult failure = buildFailure("test#timing", "Connection timeout", null);

        when(resultRepo.findLatestByTestIdAndProjectId(any(), any(), any())).thenReturn(List.of());
        when(aiClient.analyse(anyString(), anyString())).thenReturn(
                new AiAnalysisResponse(
                        new ClaudeAnalysisResult("FLAKY_TIMING", 0.85,
                                "Network timeout intermittent", "Flaky network call", "Add retry logic",
                                true, "HttpClient"),
                        180, 70));
        when(analysisRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FailureAnalysis result = service.classify(failure, projectId);
        assertThat(result.isFlakyCandidate()).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TestCaseResult buildFailure(String testId, String message, String stack) {
        TestExecution exec = new TestExecution.Builder()
                .runId(UUID.randomUUID().toString())
                .environment("ci")
                .totalTests(1).passed(0).failed(1).broken(0).skipped(0)
                .executedAt(Instant.now())
                .build();

        return TestCaseResult.builder()
                .execution(exec)
                .testId(testId)
                .status(TestStatus.FAILED)
                .failureMessage(message)
                .stackTrace(stack)
                .build();
    }
}
