package com.platform.ai.consumer;

import com.platform.ai.classification.FailureClassificationService;
import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.TestStatus;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.core.repository.TestCaseResultRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

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

    private final boolean realtimeEnabled;
    private final TeamRepository teamRepo;
    private final ProjectRepository projectRepo;
    private final TestCaseResultRepository resultRepo;
    private final FailureClassificationService classificationService;

    public AnalysisEventConsumer(
            @Value("${ai.realtime.enabled:false}") boolean realtimeEnabled,
            TeamRepository teamRepo,
            ProjectRepository projectRepo,
            TestCaseResultRepository resultRepo,
            FailureClassificationService classificationService) {
        this.realtimeEnabled        = realtimeEnabled;
        this.teamRepo               = teamRepo;
        this.projectRepo            = projectRepo;
        this.resultRepo             = resultRepo;
        this.classificationService  = classificationService;
    }

    @KafkaListener(
            topics = Topics.TEST_RESULTS_RAW,
            groupId = "${spring.kafka.consumer.group-id:platform-ai}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, UnifiedTestResult> record) {
        UnifiedTestResult run = record.value();
        if (run == null) return;

        if (!realtimeEnabled) {
            log.debug("[AI Consumer] Real-time analysis disabled — skipping run={}", run.runId());
            return;
        }

        long failCount = run.testCases().stream()
                .filter(tc -> tc.status() == TestStatus.FAILED)
                .count();

        if (failCount == 0) return;

        log.info("[AI Consumer] run={} has {} failures — resolving project", run.runId(), failCount);

        UUID projectId = resolveProjectId(run.teamId(), run.projectId());
        if (projectId == null) {
            log.warn("[AI Consumer] Could not resolve project for team={} project={}",
                    run.teamId(), run.projectId());
            return;
        }

        for (TestCaseResultDto tc : run.testCases()) {
            if (tc.status() != TestStatus.FAILED) continue;

            // Look up the persisted TestCaseResult (ingestion saves it first)
            Optional<TestCaseResult> persisted = resultRepo
                    .findByStatusSince(TestStatus.FAILED,
                            run.executedAt().minusSeconds(5))
                    .stream()
                    .filter(r -> tc.testId().equals(r.getTestId()))
                    .findFirst();

            if (persisted.isEmpty()) {
                log.debug("[AI Consumer] TestCaseResult not yet persisted for testId={} — skipping",
                        tc.testId());
                continue;
            }

            try {
                classificationService.classify(persisted.get(), projectId);
            } catch (Exception e) {
                log.warn("[AI Consumer] Classification failed for testId={}: {}",
                        tc.testId(), e.getMessage());
            }
        }
    }

    private UUID resolveProjectId(String teamSlug, String projectSlug) {
        return teamRepo.findBySlug(teamSlug)
                .flatMap(team -> projectRepo.findByTeamIdAndSlug(team.getId(), projectSlug))
                .map(project -> project.getId())
                .orElse(null);
    }
}
