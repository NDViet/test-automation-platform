package com.platform.analytics.impact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TIA dashboard endpoints — aggregated metrics for Grafana and the portal.
 *
 * <p>All endpoints are read-only and do not require authentication
 * (same permitting as other analytics query endpoints).</p>
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "TIA Trends", description = "Test Impact Analysis metrics and historical trends")
public class TiaTrendsController {

    private final TiaTrendsService trendsService;

    public TiaTrendsController(TiaTrendsService trendsService) {
        this.trendsService = trendsService;
    }

    // ── Org-level ─────────────────────────────────────────────────────────────

    @GetMapping("/tia/summary")
    @Operation(summary = "Org-level TIA summary — total requests, avg reduction, coverage breadth")
    public ResponseEntity<TiaTrendsService.TiaSummary> orgSummary(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(trendsService.orgSummary(days));
    }

    @GetMapping("/tia/coverage-breadth")
    @Operation(summary = "Coverage breadth per project — mapped tests and classes")
    public ResponseEntity<List<TiaTrendsService.ProjectCoverageBreadth>> coverageBreadthByProject() {
        return ResponseEntity.ok(trendsService.coverageBreadthByProject());
    }

    // ── Per-project ───────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/tia/risk-distribution")
    @Operation(summary = "Risk level distribution — count of LOW/MEDIUM/HIGH/CRITICAL events")
    public ResponseEntity<Map<String, Long>> riskDistribution(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(trendsService.riskDistribution(projectId, days));
    }

    @GetMapping("/{projectId}/tia/reduction-trend")
    @Operation(summary = "Daily reduction % trend — average reduction and event count per day")
    public ResponseEntity<List<TiaTrendsService.DailyReductionPoint>> reductionTrend(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(trendsService.dailyReductionTrend(projectId, days));
    }

    @GetMapping("/{projectId}/tia/coverage-breadth-trend")
    @Operation(summary = "Daily coverage breadth — distinct mapped classes and tests over time")
    public ResponseEntity<List<TiaTrendsService.DailyCoverageBreadthPoint>> coverageBreadthTrend(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(trendsService.dailyCoverageBreadth(projectId, days));
    }

    @GetMapping("/{projectId}/tia/events")
    @Operation(summary = "Recent TIA events — last N analyse() calls with outcomes")
    public ResponseEntity<List<TiaTrendsService.TiaEventDto>> recentEvents(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(trendsService.recentEvents(projectId, limit));
    }
}
