package com.platform.analytics.trends;

import com.platform.core.domain.TestExecution;
import com.platform.core.repository.TestExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes pass-rate trends, MTTR, and duration percentiles over a date window.
 */
@Service
@Transactional(readOnly = true)
public class TrendAnalysisService {

    private final TestExecutionRepository executionRepo;

    public TrendAnalysisService(TestExecutionRepository executionRepo) {
        this.executionRepo = executionRepo;
    }

    /**
     * Returns daily pass-rate data points for the given project over the last {@code days} days.
     */
    public List<PassRatePoint> dailyPassRates(UUID projectId, int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant to   = Instant.now();
        List<TestExecution> executions = executionRepo.findByProjectAndDateRange(projectId, from, to);

        // Group by UTC date and aggregate
        Map<LocalDate, List<TestExecution>> byDay = executions.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getExecutedAt().atZone(ZoneOffset.UTC).toLocalDate()));

        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<TestExecution> dayExecs = entry.getValue();
                    int total  = dayExecs.stream().mapToInt(TestExecution::getTotalTests).sum();
                    int passed = dayExecs.stream().mapToInt(TestExecution::getPassed).sum();
                    int failed = dayExecs.stream().mapToInt(e -> e.getFailed() + e.getBroken()).sum();
                    double rate = total > 0 ? (double) passed / total : 0.0;
                    return new PassRatePoint(entry.getKey(), rate, total, passed, failed);
                })
                .toList();
    }

    /**
     * Mean Time to Recovery: average duration (in minutes) from first failure in a streak
     * to the first subsequent pass, measured across the lookback window.
     */
    public OptionalDouble meanTimeToRecoveryMinutes(UUID projectId, int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        List<TestExecution> executions = executionRepo
                .findByProjectAndDateRange(projectId, from, Instant.now());

        // Sort chronologically
        List<TestExecution> sorted = executions.stream()
                .sorted(Comparator.comparing(TestExecution::getExecutedAt))
                .toList();

        List<Long> recoveryMinutes = new ArrayList<>();
        Instant failureStart = null;

        for (TestExecution exec : sorted) {
            boolean hasFailed = exec.getFailed() + exec.getBroken() > 0;
            if (hasFailed && failureStart == null) {
                failureStart = exec.getExecutedAt();
            } else if (!hasFailed && failureStart != null) {
                long minutes = ChronoUnit.MINUTES.between(failureStart, exec.getExecutedAt());
                recoveryMinutes.add(minutes);
                failureStart = null;
            }
        }

        return recoveryMinutes.stream().mapToLong(Long::longValue).average();
    }

    /**
     * Duration percentiles (p50, p95) in milliseconds across all executions in the window.
     */
    public DurationStats durationStats(UUID projectId, int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        List<TestExecution> executions = executionRepo
                .findByProjectAndDateRange(projectId, from, Instant.now());

        List<Long> durations = executions.stream()
                .filter(e -> e.getDurationMs() != null)
                .map(TestExecution::getDurationMs)
                .sorted()
                .toList();

        if (durations.isEmpty()) return new DurationStats(0, 0, 0);

        long p50 = percentile(durations, 50);
        long p95 = percentile(durations, 95);
        long max = durations.getLast();
        return new DurationStats(p50, p95, max);
    }

    private long percentile(List<Long> sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    public record DurationStats(long p50Ms, long p95Ms, long maxMs) {}
}
