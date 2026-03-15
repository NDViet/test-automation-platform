package com.platform.ai.classification;

import com.platform.ai.client.AiAnalysisResponse;
import com.platform.ai.client.AiClient;
import com.platform.core.domain.FailureAnalysis;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.repository.FailureAnalysisRepository;
import com.platform.core.repository.TestCaseResultRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates failure analysis: builds the prompt, calls the AI provider,
 * persists the resulting {@link FailureAnalysis}, and emits Micrometer metrics.
 *
 * <p>Idempotent: if a {@link FailureAnalysis} already exists for the given
 * {@code testCaseResultId}, the existing record is returned without another API call.</p>
 */
@Service
public class FailureClassificationService {

    private static final Logger log = LoggerFactory.getLogger(FailureClassificationService.class);
    private static final int HISTORY_LIMIT = 5;

    private final AiClient aiClient;
    private final PromptBuilder promptBuilder;
    private final TestCaseResultRepository resultRepo;
    private final FailureAnalysisRepository analysisRepo;
    private final MeterRegistry meterRegistry;

    public FailureClassificationService(AiClient aiClient,
                                        PromptBuilder promptBuilder,
                                        TestCaseResultRepository resultRepo,
                                        FailureAnalysisRepository analysisRepo,
                                        MeterRegistry meterRegistry) {
        this.aiClient      = aiClient;
        this.promptBuilder = promptBuilder;
        this.resultRepo    = resultRepo;
        this.analysisRepo  = analysisRepo;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Classifies the given failed test result and persists the analysis.
     *
     * @param failure   the FAILED {@link TestCaseResult} to analyse
     * @param projectId the project this result belongs to
     * @return persisted {@link FailureAnalysis}
     */
    @Transactional
    public FailureAnalysis classify(TestCaseResult failure, UUID projectId) {
        return classify(failure, projectId, Map.of());
    }

    /**
     * Classify with additional diagnostic context (DOM snapshot, selector) forwarded
     * from the testkit via the test result environment map.
     *
     * @param diagnostics key-value pairs from {@code TestCaseResultDto.environment()} —
     *                    may contain {@code platform.diagnostic.dom} and
     *                    {@code platform.diagnostic.selector}
     */
    @Transactional
    public FailureAnalysis classify(TestCaseResult failure, UUID projectId,
                                    Map<String, String> diagnostics) {
        if (analysisRepo.existsSuccessfulAnalysis(failure.getId())) {
            log.debug("[AI] Already successfully analysed testCaseResultId={}", failure.getId());
            return analysisRepo
                    .findTopByTestIdAndProjectIdOrderByAnalysedAtDesc(failure.getTestId(), projectId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Analysis flag set but record not found for testId=" + failure.getTestId()));
        }

        List<TestCaseResult> history = resultRepo.findLatestByTestIdAndProjectId(
                failure.getTestId(), projectId, PageRequest.of(0, HISTORY_LIMIT));

        String userPrompt = promptBuilder.buildUserPrompt(
                failure.getTestId(),
                failure.getFailureMessage(),
                failure.getStackTrace(),
                history,
                diagnostics);

        log.info("[AI] Classifying failure testId={} projectId={} provider={} hasDom={}",
                failure.getTestId(), projectId, aiClient.providerName(),
                diagnostics.containsKey("platform.diagnostic.dom"));

        AiAnalysisResponse response;
        try {
            response = aiClient.analyse(PromptBuilder.SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            // Persist an ERROR record so the failure is visible and retried next time.
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[AI] API call failed for testId={}: {}", failure.getTestId(), errorMsg);
            FailureAnalysis errorRecord = FailureAnalysis.builder()
                    .testId(failure.getTestId())
                    .projectId(projectId)
                    .testCaseResultId(failure.getId())
                    .category("UNKNOWN")
                    .confidence(0.0)
                    .rootCause("AI analysis failed — will be retried")
                    .modelVersion(aiClient.providerName())
                    .analysisStatus("ERROR")
                    .errorMessage(errorMsg)
                    .build();
            analysisRepo.save(errorRecord);
            throw e;   // re-throw so batch/on-demand callers can count/log it
        }

        ClaudeAnalysisResult result = response.result();

        FailureAnalysis analysis = FailureAnalysis.builder()
                .testId(failure.getTestId())
                .projectId(projectId)
                .testCaseResultId(failure.getId())
                .category(result.category())
                .confidence(result.confidence())
                .rootCause(result.rootCause())
                .detailedAnalysis(result.detailedAnalysis())
                .suggestedFix(result.suggestedFix())
                .flakyCandidate(result.flakyCandidate())
                .affectedComponent(result.affectedComponent())
                .modelVersion(aiClient.providerName())
                .inputTokens(response.inputTokens())
                .outputTokens(response.outputTokens())
                .analysisStatus("SUCCESS")
                .build();

        FailureAnalysis saved = analysisRepo.save(analysis);
        log.info("[AI] Saved analysis id={} category={} confidence={} tokens=in:{} out:{}",
                saved.getId(), saved.getCategory(), saved.getConfidence(),
                response.inputTokens(), response.outputTokens());

        // Metrics
        String provider = aiClient.providerName();
        Counter.builder("ai.tokens.input")
                .description("Cumulative AI input tokens consumed")
                .tag("provider", provider)
                .register(meterRegistry)
                .increment(response.inputTokens());
        Counter.builder("ai.tokens.output")
                .description("Cumulative AI output tokens consumed")
                .tag("provider", provider)
                .register(meterRegistry)
                .increment(response.outputTokens());
        Counter.builder("ai.analyses.total")
                .description("Total AI failure analyses performed")
                .tag("provider", provider)
                .tag("category", result.category())
                .register(meterRegistry)
                .increment();

        return saved;
    }
}
