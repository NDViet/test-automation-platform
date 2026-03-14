package com.platform.analytics.report;

import com.platform.analytics.trends.QualityGateEvaluator;
import com.platform.analytics.trends.QualityGateResult;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.Project;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestExecutionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a quality report covering all test executions in a given time window.
 *
 * <p>Typical use cases:</p>
 * <ul>
 *   <li>Release gate: called by CI just before a deployment tag is created</li>
 *   <li>Weekly digest: auto-generated every Monday morning</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ReleaseReportService {

    private static final int TOP_FLAKY_LIMIT     = 10;
    private static final int TOP_FAILING_LIMIT   = 10;

    private final TestExecutionRepository executionRepo;
    private final TestCaseResultRepository resultRepo;
    private final FlakinessScoreRepository scoreRepo;
    private final ProjectRepository projectRepo;
    private final QualityGateEvaluator gateEvaluator;

    public ReleaseReportService(TestExecutionRepository executionRepo,
                                 TestCaseResultRepository resultRepo,
                                 FlakinessScoreRepository scoreRepo,
                                 ProjectRepository projectRepo,
                                 QualityGateEvaluator gateEvaluator) {
        this.executionRepo = executionRepo;
        this.resultRepo    = resultRepo;
        this.scoreRepo     = scoreRepo;
        this.projectRepo   = projectRepo;
        this.gateEvaluator = gateEvaluator;
    }

    /**
     * Generates a release report for a project over a time window.
     *
     * @param projectId  the project to report on
     * @param from       start of window (inclusive)
     * @param to         end of window (inclusive)
     * @param releaseTag optional release label (e.g. "v1.2.0")
     */
    public ReleaseReportDto generate(UUID projectId, Instant from, Instant to, String releaseTag) {
        Project project = projectRepo.findById(projectId).orElseThrow(
                () -> new IllegalArgumentException("Project not found: " + projectId));

        List<TestExecution> executions = executionRepo.findByProjectAndDateRange(projectId, from, to);

        // Aggregate execution stats
        int totalRuns    = executions.size();
        int totalTests   = executions.stream().mapToInt(TestExecution::getTotalTests).sum();
        int totalPassed  = executions.stream().mapToInt(TestExecution::getPassed).sum();
        int totalFailed  = executions.stream().mapToInt(TestExecution::getFailed).sum();
        int totalBroken  = executions.stream().mapToInt(TestExecution::getBroken).sum();
        int totalSkipped = executions.stream().mapToInt(TestExecution::getSkipped).sum();
        double passRate  = totalTests > 0 ? (double) totalPassed / totalTests : 0.0;

        // Flakiness summary
        List<FlakinessScore> allScores = scoreRepo.findTopFlakyByProject(
                projectId, PageRequest.of(0, 10_000));
        int flakyCount    = (int) allScores.stream()
                .filter(s -> s.getClassification() == FlakinessScore.Classification.FLAKY
                        || s.getClassification() == FlakinessScore.Classification.CRITICAL_FLAKY)
                .count();
        List<FlakinessScore> criticalScores = allScores.stream()
                .filter(s -> s.getClassification() == FlakinessScore.Classification.CRITICAL_FLAKY)
                .limit(TOP_FLAKY_LIMIT)
                .toList();
        List<String> topCritical = criticalScores.stream()
                .map(FlakinessScore::getTestId)
                .toList();

        // Top failing tests by failure count in window
        List<ReleaseReportDto.TopFailingTest> topFailing = buildTopFailing(executions, totalRuns);

        // Quality gate: evaluate against latest run in window
        QualityGateResult gate = evaluateGate(executions, projectId);

        return new ReleaseReportDto(
                projectId,
                project.getSlug(),
                releaseTag,
                from,
                to,
                totalRuns,
                totalTests,
                totalPassed,
                totalFailed,
                totalBroken,
                totalSkipped,
                passRate,
                flakyCount,
                criticalScores.size(),
                topCritical,
                Map.of(), // AI category breakdown requires platform-ai — left empty here
                topFailing,
                gate.passed(),
                gate.violations(),
                Instant.now()
        );
    }

    private List<ReleaseReportDto.TopFailingTest> buildTopFailing(
            List<TestExecution> executions, int totalRuns) {
        if (executions.isEmpty()) return List.of();

        // Aggregate failure count per testId across all executions in window
        Map<String, Integer> failCounts = new HashMap<>();
        for (TestExecution exec : executions) {
            List<TestCaseResult> results = resultRepo.findByExecutionIdAndStatus(
                    exec.getId(), TestStatus.FAILED);
            for (TestCaseResult r : results) {
                failCounts.merge(r.getTestId(), 1, Integer::sum);
            }
        }

        return failCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_FAILING_LIMIT)
                .map(e -> new ReleaseReportDto.TopFailingTest(
                        e.getKey(),
                        e.getValue(),
                        totalRuns > 0 ? (double) e.getValue() / totalRuns : 0.0))
                .toList();
    }

    private QualityGateResult evaluateGate(List<TestExecution> executions, UUID projectId) {
        if (executions.isEmpty()) {
            return QualityGateResult.pass(0.0, 0);
        }
        // Evaluate against the latest execution in the window
        TestExecution latest = executions.get(0); // ordered DESC by executedAt
        String teamSlug = latest.getProject().getTeam() != null
                ? latest.getProject().getTeam().getSlug() : "unknown";
        UnifiedTestResult stub = new UnifiedTestResult(
                latest.getRunId(), teamSlug, latest.getProject().getSlug(),
                latest.getBranch(), latest.getEnvironment(), latest.getCommitSha(),
                latest.getTriggerType(), latest.getCiProvider(), latest.getCiRunUrl(),
                latest.getExecutedAt(),
                latest.getTotalTests(), latest.getPassed(), latest.getFailed(),
                latest.getSkipped(), latest.getBroken(),
                latest.getDurationMs(), latest.getSourceFormat(),
                List.of(),
                latest.getExecutionMode(), latest.getParallelism(), latest.getSuiteName()
        );
        return gateEvaluator.evaluate(stub, projectId);
    }
}
