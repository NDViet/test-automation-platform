package com.platform.ai.batch;

import com.platform.ai.classification.FailureClassificationService;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.repository.FailureAnalysisRepository;
import com.platform.core.repository.TestCaseResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job that runs nightly (default: 02:00 UTC) to classify any
 * FAILED test results from the previous 24 hours that have not yet been
 * analysed by Claude.
 *
 * <p>Configure via {@code ai.batch.cron} in {@code application.yml}.</p>
 */
@Component
public class NightlyAnalysisBatchJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyAnalysisBatchJob.class);

    private final TestCaseResultRepository resultRepo;
    private final FailureAnalysisRepository analysisRepo;
    private final FailureClassificationService classificationService;

    public NightlyAnalysisBatchJob(TestCaseResultRepository resultRepo,
                                    FailureAnalysisRepository analysisRepo,
                                    FailureClassificationService classificationService) {
        this.resultRepo              = resultRepo;
        this.analysisRepo            = analysisRepo;
        this.classificationService   = classificationService;
    }

    @Scheduled(cron = "${ai.batch.cron:0 0 2 * * *}")
    public void run() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        log.info("[AI Batch] Starting nightly failure classification since={}", since);

        List<TestCaseResult> failures = resultRepo
                .findByStatusSince(TestStatus.FAILED, since)
                .stream()
                .filter(r -> !analysisRepo.existsByTestCaseResultId(r.getId()))
                .toList();

        log.info("[AI Batch] Found {} unanalysed failures in the last 24 h", failures.size());

        int success = 0;
        int errors  = 0;
        for (TestCaseResult failure : failures) {
            try {
                UUID projectId = failure.getExecution().getProject().getId();
                classificationService.classify(failure, projectId);
                success++;
            } catch (Exception e) {
                log.warn("[AI Batch] Failed to classify testId={}: {}",
                        failure.getTestId(), e.getMessage());
                errors++;
            }
        }

        log.info("[AI Batch] Complete — classified={} errors={}", success, errors);
    }
}
