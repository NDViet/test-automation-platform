package com.platform.analytics.api;

import com.platform.analytics.alerts.AlertHistoryService;
import com.platform.analytics.trends.QualityGateEvaluator;
import com.platform.analytics.trends.QualityGateResult;
import com.platform.core.domain.AlertHistory;
import com.platform.core.domain.TestExecution;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TestExecutionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exposes alert history and CI-oriented quality gate endpoints.
 *
 * <pre>
 * GET  /api/v1/alerts/projects/{projectId}?days=7       — recent alerts for a project
 * GET  /api/v1/alerts/org?days=7                         — all recent alerts (org-wide)
 * GET  /api/v1/projects/{projectId}/quality-gate/ci      — CI-friendly gate (200 or 422)
 * </pre>
 */
@RestController
@Tag(name = "Alerts & Quality Gate", description = "Alert history and CI quality gate enforcement")
public class AlertController {

    private final AlertHistoryService historyService;
    private final QualityGateEvaluator gateEvaluator;
    private final TestExecutionRepository executionRepo;
    private final ProjectRepository projectRepo;

    public AlertController(AlertHistoryService historyService,
                           QualityGateEvaluator gateEvaluator,
                           TestExecutionRepository executionRepo,
                           ProjectRepository projectRepo) {
        this.historyService = historyService;
        this.gateEvaluator  = gateEvaluator;
        this.executionRepo  = executionRepo;
        this.projectRepo    = projectRepo;
    }

    // ── Alert history ─────────────────────────────────────────────────────────

    @GetMapping("/api/v1/alerts/projects/{projectId}")
    @Operation(summary = "Recent alerts fired for a project")
    public List<AlertHistory> projectAlerts(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "7") int days) {
        return historyService.recentForProject(projectId, days);
    }

    @GetMapping("/api/v1/alerts/org")
    @Operation(summary = "All alerts fired across the organisation")
    public List<AlertHistory> orgAlerts(@RequestParam(defaultValue = "7") int days) {
        return historyService.recentAll(days);
    }

    // ── CI quality gate ───────────────────────────────────────────────────────

    /**
     * CI-friendly quality gate endpoint.
     *
     * <ul>
     *   <li>HTTP 200 — gate passed; safe to deploy</li>
     *   <li>HTTP 422 — gate failed; block deployment</li>
     *   <li>HTTP 404 — no runs found for project</li>
     * </ul>
     *
     * <p>Integrate in CI with:
     * {@code curl --fail https://platform/api/v1/projects/$ID/quality-gate/ci}</p>
     */
    @GetMapping("/api/v1/projects/{projectId}/quality-gate/ci")
    @Operation(summary = "CI quality gate — returns 200 (pass) or 422 (fail)")
    public ResponseEntity<Map<String, Object>> ciGate(@PathVariable UUID projectId) {
        return executionRepo.findTopByProjectIdOrderByExecutedAtDesc(projectId)
                .map(exec -> {
                    QualityGateResult gate = gateEvaluator.evaluate(buildStub(exec), projectId);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("projectId", projectId);
                    body.put("runId", exec.getRunId());
                    body.put("passed", gate.passed());
                    body.put("passRate", String.format("%.2f%%", gate.actualPassRate() * 100));
                    body.put("newFailures", gate.newFailures());
                    body.put("violations", gate.violations());
                    HttpStatus status = gate.passed() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
                    return ResponseEntity.status(status).body(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UnifiedTestResult buildStub(TestExecution exec) {
        String teamSlug = exec.getProject().getTeam() != null
                ? exec.getProject().getTeam().getSlug() : "unknown";
        return new UnifiedTestResult(
                exec.getRunId(), teamSlug, exec.getProject().getSlug(),
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
}
