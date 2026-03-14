package com.platform.analytics.consumer;

import com.platform.analytics.alerts.AlertRuleEngine;
import com.platform.analytics.flakiness.FlakinessScoringService;
import com.platform.analytics.trends.QualityGateEvaluator;
import com.platform.analytics.trends.QualityGateResult;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.Project;
import com.platform.core.domain.Team;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consumes {@link UnifiedTestResult} events from the {@code test.results.raw} topic
 * and triggers flakiness scoring, quality gate evaluation, and alert rule evaluation.
 */
@Component
public class ResultAnalysisConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultAnalysisConsumer.class);

    private final TeamRepository teamRepo;
    private final ProjectRepository projectRepo;
    private final FlakinessScoringService scoringService;
    private final QualityGateEvaluator gateEvaluator;
    private final AlertRuleEngine alertRuleEngine;

    public ResultAnalysisConsumer(TeamRepository teamRepo,
                                  ProjectRepository projectRepo,
                                  FlakinessScoringService scoringService,
                                  QualityGateEvaluator gateEvaluator,
                                  AlertRuleEngine alertRuleEngine) {
        this.teamRepo        = teamRepo;
        this.projectRepo     = projectRepo;
        this.scoringService  = scoringService;
        this.gateEvaluator   = gateEvaluator;
        this.alertRuleEngine = alertRuleEngine;
    }

    @KafkaListener(
            topics = Topics.TEST_RESULTS_RAW,
            groupId = "${spring.kafka.consumer.group-id:platform-analytics}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, UnifiedTestResult> record) {
        UnifiedTestResult result = record.value();
        if (result == null) return;

        log.info("[Analytics] Received run={} team={} project={} tests={}",
                result.runId(), result.teamId(), result.projectId(), result.total());

        // Resolve project UUID (ingestion stores team/project as slugs)
        UUID projectId = resolveProjectId(result.teamId(), result.projectId());
        if (projectId == null) {
            log.warn("[Analytics] Could not resolve project for team={} project={} — skipping",
                    result.teamId(), result.projectId());
            return;
        }

        // 1. Flakiness scoring for all unique test IDs in this run
        triggerFlakinessScoring(result, projectId);

        // 2. Quality gate evaluation
        QualityGateResult gate = gateEvaluator.evaluate(result, projectId);
        if (!gate.passed()) {
            log.warn("[Analytics] Quality gate FAILED for run={} violations={}",
                    result.runId(), gate.violations());
        } else {
            log.info("[Analytics] Quality gate PASSED for run={} passRate={}%",
                    result.runId(), String.format("%.1f", gate.actualPassRate() * 100));
        }

        // 3. Alert rule evaluation (with projectId enables CRITICAL_FLAKY metric + history)
        alertRuleEngine.evaluate(result, projectId);
    }

    private UUID resolveProjectId(String teamSlug, String projectSlug) {
        Optional<Team> team = teamRepo.findBySlug(teamSlug);
        if (team.isEmpty()) return null;
        return projectRepo.findByTeamIdAndSlug(team.get().getId(), projectSlug)
                .map(Project::getId)
                .orElse(null);
    }

    private void triggerFlakinessScoring(UnifiedTestResult result, UUID projectId) {
        // Collect unique test IDs in this run
        Set<String> testIds = result.testCases().stream()
                .map(tc -> tc.testId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (testIds.isEmpty()) return;

        log.debug("[Analytics] Scoring {} unique tests for project={}", testIds.size(), projectId);
        int scored = 0;
        for (String testId : testIds) {
            try {
                scoringService.computeAndPersist(testId, projectId);
                scored++;
            } catch (Exception e) {
                log.warn("[Analytics] Failed to score testId={}: {}", testId, e.getMessage());
            }
        }
        log.debug("[Analytics] Scored {}/{} tests for run={}", scored, testIds.size(), result.runId());
    }
}
