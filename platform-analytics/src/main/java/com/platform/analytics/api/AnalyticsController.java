package com.platform.analytics.api;

import com.platform.analytics.api.dto.FlakinessDto;
import com.platform.analytics.api.dto.OrgSummaryDto;
import com.platform.analytics.flakiness.FlakinessReportService;
import com.platform.analytics.trends.PassRatePoint;
import com.platform.analytics.trends.QualityGateEvaluator;
import com.platform.analytics.trends.QualityGateResult;
import com.platform.analytics.trends.TrendAnalysisService;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.Project;
import com.platform.core.domain.Team;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.core.repository.TestExecutionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Flakiness scores, trends, quality gates, and org summary")
public class AnalyticsController {

    private final FlakinessReportService flakinessService;
    private final TrendAnalysisService trendService;
    private final QualityGateEvaluator gateEvaluator;
    private final TeamRepository teamRepo;
    private final ProjectRepository projectRepo;
    private final TestExecutionRepository executionRepo;
    private final FlakinessScoreRepository scoreRepo;

    public AnalyticsController(FlakinessReportService flakinessService,
                                TrendAnalysisService trendService,
                                QualityGateEvaluator gateEvaluator,
                                TeamRepository teamRepo,
                                ProjectRepository projectRepo,
                                TestExecutionRepository executionRepo,
                                FlakinessScoreRepository scoreRepo) {
        this.flakinessService = flakinessService;
        this.trendService      = trendService;
        this.gateEvaluator     = gateEvaluator;
        this.teamRepo          = teamRepo;
        this.projectRepo       = projectRepo;
        this.executionRepo     = executionRepo;
        this.scoreRepo         = scoreRepo;
    }

    // ── Flakiness ─────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/flakiness")
    @Operation(summary = "Top flaky tests for a project")
    public List<FlakinessDto> topFlaky(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String classification) {

        if (classification != null) {
            FlakinessScore.Classification cls =
                    FlakinessScore.Classification.valueOf(classification.toUpperCase());
            return flakinessService.getTopFlakyByClassification(projectId, cls, limit)
                    .stream().map(FlakinessDto::from).toList();
        }
        return flakinessService.getTopFlakyForProject(projectId, limit)
                .stream().map(FlakinessDto::from).toList();
    }

    // ── Trends ────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/trends/pass-rate")
    @Operation(summary = "Daily pass-rate trend for a project")
    public List<PassRatePoint> passRateTrend(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return trendService.dailyPassRates(projectId, days);
    }

    @GetMapping("/{projectId}/trends/mttr")
    @Operation(summary = "Mean Time to Recovery (minutes)")
    public Map<String, Object> mttr(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        OptionalDouble mttr = trendService.meanTimeToRecoveryMinutes(projectId, days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("lookbackDays", days);
        result.put("mttrMinutes", mttr.isPresent() ? mttr.getAsDouble() : null);
        return result;
    }

    @GetMapping("/{projectId}/trends/duration")
    @Operation(summary = "Duration stats (p50/p95) in milliseconds")
    public TrendAnalysisService.DurationStats durationStats(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return trendService.durationStats(projectId, days);
    }

    // ── Quality Gate ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/quality-gate")
    @Operation(summary = "Evaluate quality gate against the latest run of a project")
    @Transactional(readOnly = true)
    public ResponseEntity<QualityGateResult> qualityGate(@PathVariable UUID projectId) {
        return executionRepo.findTopByProjectIdOrderByExecutedAtDesc(projectId)
                .map(exec -> {
                    UnifiedTestResult stub = buildStubResult(exec);
                    return ResponseEntity.ok(gateEvaluator.evaluate(stub, projectId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Org Summary ───────────────────────────────────────────────────────────

    @GetMapping("/org/summary")
    @Operation(summary = "Organisation-wide quality summary across all projects")
    @Transactional(readOnly = true)
    public OrgSummaryDto orgSummary(
            @RequestParam(required = false) String teamSlug,
            @RequestParam(defaultValue = "7") int days) {

        List<Project> projects;
        if (teamSlug != null) {
            Optional<Team> team = teamRepo.findBySlug(teamSlug);
            if (team.isEmpty()) return emptyOrgSummary();
            projects = projectRepo.findByTeamId(team.get().getId());
        } else {
            projects = projectRepo.findAll();
        }
        if (projects.isEmpty()) return emptyOrgSummary();

        Set<UUID> projectIds = projects.stream().map(Project::getId).collect(Collectors.toSet());
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<TestExecution> executions = executionRepo.findByProjectIdsAndSince(projectIds, since);

        // Group by project
        Map<UUID, List<TestExecution>> byProject = executions.stream()
                .collect(Collectors.groupingBy(e -> e.getProject().getId()));

        int totalPassed = executions.stream().mapToInt(TestExecution::getPassed).sum();
        int totalTests  = executions.stream().mapToInt(TestExecution::getTotalTests).sum();
        double overallRate = totalTests > 0 ? (double) totalPassed / totalTests : 0.0;

        int criticalFlaky = (int) scoreRepo
                .findTopFlakyAcrossOrg(PageRequest.of(0, 10_000))
                .stream()
                .filter(s -> s.getClassification() == FlakinessScore.Classification.CRITICAL_FLAKY
                        && projectIds.contains(s.getProjectId()))
                .count();

        List<OrgSummaryDto.ProjectSummary> summaries = projects.stream().map(p -> {
            List<TestExecution> pExecs = byProject.getOrDefault(p.getId(), List.of());
            int pTotal  = pExecs.stream().mapToInt(TestExecution::getTotalTests).sum();
            int pPassed = pExecs.stream().mapToInt(TestExecution::getPassed).sum();
            double pRate = pTotal > 0 ? (double) pPassed / pTotal : 0.0;
            int flakyCount = (int) scoreRepo
                    .findTopFlakyByProject(p.getId(), PageRequest.of(0, 10_000))
                    .stream()
                    .filter(s -> s.getClassification() == FlakinessScore.Classification.FLAKY
                            || s.getClassification() == FlakinessScore.Classification.CRITICAL_FLAKY)
                    .count();
            String teamSlugVal = p.getTeam() != null ? p.getTeam().getSlug() : "unknown";
            return new OrgSummaryDto.ProjectSummary(p.getSlug(), teamSlugVal, pRate, pExecs.size(), flakyCount);
        }).toList();

        return new OrgSummaryDto(projects.size(), executions.size(), overallRate, criticalFlaky, summaries);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UnifiedTestResult buildStubResult(TestExecution exec) {
        String teamSlugVal = exec.getProject().getTeam() != null
                ? exec.getProject().getTeam().getSlug() : "unknown";
        return new UnifiedTestResult(
                exec.getRunId(),
                teamSlugVal,
                exec.getProject().getSlug(),
                exec.getBranch(), exec.getEnvironment(), exec.getCommitSha(),
                exec.getTriggerType(), exec.getCiProvider(), exec.getCiRunUrl(),
                exec.getExecutedAt(),
                exec.getTotalTests(), exec.getPassed(), exec.getFailed(),
                exec.getSkipped(), exec.getBroken(),
                exec.getDurationMs(), exec.getSourceFormat(),
                List.of(),
                exec.getExecutionMode(), exec.getParallelism(), exec.getSuiteName(),
                null, null
        );
    }

    private OrgSummaryDto emptyOrgSummary() {
        return new OrgSummaryDto(0, 0, 0.0, 0, List.of());
    }
}
