package com.platform.analytics.impact;

import com.platform.core.repository.TestCoverageMappingRepository;
import com.platform.core.repository.TiaEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Aggregates TIA event and coverage breadth data for dashboard queries.
 *
 * <p>All queries are read-only projections over {@code tia_events} and
 * {@code test_coverage_mappings}.</p>
 */
@Service
@Transactional(readOnly = true)
public class TiaTrendsService {

    private final TiaEventRepository            tiaEventRepo;
    private final TestCoverageMappingRepository coverageRepo;

    public TiaTrendsService(TiaEventRepository tiaEventRepo,
                            TestCoverageMappingRepository coverageRepo) {
        this.tiaEventRepo = tiaEventRepo;
        this.coverageRepo = coverageRepo;
    }

    // ── Org-level stats ───────────────────────────────────────────────────────

    /** High-level numbers for the overview row: requests, avg reduction, coverage breadth. */
    public TiaSummary orgSummary(int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        long   totalRequests  = tiaEventRepo.countAllSince(from);
        Double avgReduction   = tiaEventRepo.avgReductionPctAll(from);
        List<Object[]> breadth = coverageRepo.coverageBreadthByProject();
        long totalMappedTests   = breadth.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();
        long totalMappedClasses = breadth.stream().mapToLong(r -> ((Number) r[3]).longValue()).sum();
        return new TiaSummary(totalRequests, avgReduction, totalMappedTests, totalMappedClasses,
                              (long) breadth.size());
    }

    // ── Per-project stats ─────────────────────────────────────────────────────

    /** Risk level distribution for a project — used by the pie chart. */
    public Map<String, Long> riskDistribution(UUID projectId, int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = tiaEventRepo.countByRiskLevel(projectId, from);
        Map<String, Long> result = new LinkedHashMap<>();
        for (RiskLevel r : RiskLevel.values()) result.put(r.name(), 0L);
        for (Object[] row : rows) result.put((String) row[0], ((Number) row[1]).longValue());
        return result;
    }

    /** Daily reduction % and request count — used by the reduction trend timeseries. */
    public List<DailyReductionPoint> dailyReductionTrend(UUID projectId, int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = tiaEventRepo.dailyStatsForProject(projectId, from);
        List<DailyReductionPoint> points = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate day = ((java.sql.Timestamp) row[0])
                    .toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            double avgReduction  = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            long   eventCount    = ((Number) row[2]).longValue();
            double avgSelected   = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            double avgTotal      = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            points.add(new DailyReductionPoint(day, avgReduction, eventCount, avgSelected, avgTotal));
        }
        return points;
    }

    /** Daily coverage breadth — distinct classes and tests seen per day. */
    public List<DailyCoverageBreadthPoint> dailyCoverageBreadth(UUID projectId, int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = coverageRepo.dailyCoverageBreadth(projectId, from);
        List<DailyCoverageBreadthPoint> points = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate day = ((java.sql.Timestamp) row[0])
                    .toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            long distinctClasses = ((Number) row[1]).longValue();
            long distinctTests   = ((Number) row[2]).longValue();
            points.add(new DailyCoverageBreadthPoint(day, distinctClasses, distinctTests));
        }
        return points;
    }

    /** Coverage breadth per project — for the org-level coverage table. */
    public List<ProjectCoverageBreadth> coverageBreadthByProject() {
        List<Object[]> rows = coverageRepo.coverageBreadthByProject();
        List<ProjectCoverageBreadth> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new ProjectCoverageBreadth(
                    (String) row[0], (String) row[1],
                    ((Number) row[2]).longValue(), ((Number) row[3]).longValue(),
                    row[4] != null ? ((java.sql.Timestamp) row[4]).toInstant() : null));
        }
        return result;
    }

    /** Recent TIA events for a project — for the events history table. */
    public List<TiaEventDto> recentEvents(UUID projectId, int limit) {
        return tiaEventRepo.findByProjectIdOrderByQueriedAtDesc(projectId)
                .stream().limit(limit)
                .map(e -> new TiaEventDto(
                        e.getId(), e.getQueriedAt(), e.getChangedClasses(),
                        e.getTotalTests(), e.getSelectedTests(), e.getUncoveredClasses(),
                        e.getReductionPct(), e.getRiskLevel(), e.getBranch(), e.getTriggeredBy()))
                .toList();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record TiaSummary(
            long   totalRequests,
            Double avgReductionPct,
            long   totalMappedTests,
            long   totalMappedClasses,
            long   projectsWithTia) {}

    public record DailyReductionPoint(
            LocalDate day,
            double    avgReductionPct,
            long      eventCount,
            double    avgSelectedTests,
            double    avgTotalTests) {}

    public record DailyCoverageBreadthPoint(
            LocalDate day,
            long      distinctClasses,
            long      distinctTests) {}

    public record ProjectCoverageBreadth(
            String  projectSlug,
            String  teamSlug,
            long    distinctClasses,
            long    distinctTests,
            Instant lastSeenAt) {}

    public record TiaEventDto(
            UUID    id,
            Instant queriedAt,
            int     changedClasses,
            int     totalTests,
            int     selectedTests,
            int     uncoveredClasses,
            Double  reductionPct,
            String  riskLevel,
            String  branch,
            String  triggeredBy) {}
}
