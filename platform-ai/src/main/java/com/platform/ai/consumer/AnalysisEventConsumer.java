package com.platform.ai.consumer;

import com.platform.ai.classification.FailureClassificationService;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.PlatformSettingRepository;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestExecutionRepository;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.platform.common.enums.TestStatus.BROKEN;
import static com.platform.common.enums.TestStatus.FAILED;

/**
 * Consumes {@link UnifiedTestResult} events from {@code test.results.raw} and,
 * when real-time AI analysis is enabled, immediately classifies any FAILED
 * test cases using Claude.
 *
 * <p>Enable via {@code ai.realtime.enabled=true} (default: false) to avoid
 * incurring Claude API costs on every run during initial rollout.</p>
 */
@Component
public class AnalysisEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEventConsumer.class);

    private static final String KEY_REALTIME = "ai.realtime.enabled";

    private final PlatformSettingRepository settingRepo;
    private final TestCaseResultRepository resultRepo;
    private final TestExecutionRepository executionRepo;
    private final FailureClassificationService classificationService;

    public AnalysisEventConsumer(
            PlatformSettingRepository settingRepo,
            TestCaseResultRepository resultRepo,
            TestExecutionRepository executionRepo,
            FailureClassificationService classificationService) {
        this.settingRepo            = settingRepo;
        this.resultRepo             = resultRepo;
        this.executionRepo          = executionRepo;
        this.classificationService  = classificationService;
    }

    @PostConstruct
    void logMode() {
        log.info("[AI Consumer] Started — real-time analysis is controlled via AI Settings in the portal (currently: {})",
                isRealtimeEnabled() ? "ENABLED" : "DISABLED");
    }

    private boolean isRealtimeEnabled() {
        return settingRepo.findById(KEY_REALTIME)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(false);
    }

    @KafkaListener(
            topics = Topics.TEST_RESULTS_RAW,
            groupId = "${spring.kafka.consumer.group-id:platform-ai}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, UnifiedTestResult> record) {
        UnifiedTestResult run = record.value();
        if (run == null) return;

        if (!isRealtimeEnabled()) {
            return;
        }

        long failCount = run.testCases().stream()
                .filter(tc -> tc.status() == FAILED || tc.status() == BROKEN)
                .count();

        if (failCount == 0) return;

        log.info("[AI Consumer] run={} has {} failure(s) — classifying", run.runId(), failCount);

        // Look up the persisted execution by its stable runId (written by ingestion before Kafka publish)
        Optional<TestExecution> execution = executionRepo.findByRunId(run.runId());
        if (execution.isEmpty()) {
            log.warn("[AI Consumer] Execution not found in DB for runId={} — ingestion may not have committed yet",
                    run.runId());
            return;
        }

        UUID projectId = execution.get().getProject().getId();
        List<TestCaseResult> failures = resultRepo
                .findByExecutionIdAndStatusIn(execution.get().getId(), List.of(FAILED, BROKEN));

        for (TestCaseResult failure : failures) {
            try {
                classificationService.classify(failure, projectId);
            } catch (Exception e) {
                log.warn("[AI Consumer] Classification failed for testId={}: {}",
                        failure.getTestId(), e.getMessage());
            }
        }
    }

}
