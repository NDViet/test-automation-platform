package com.platform.analytics.trends;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.TestStatus;
import com.platform.core.repository.TestExecutionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Evaluates configurable quality gates against an incoming test run.
 *
 * <p>Gates are configured via application properties:</p>
 * <pre>
 *   platform.analytics.gate.min-pass-rate=0.80
 *   platform.analytics.gate.max-new-failures=10
 * </pre>
 *
 * <p>Used in CI pipelines to block deployments when quality drops below threshold.</p>
 */
@Service
public class QualityGateEvaluator {

    private final double minPassRate;
    private final int maxNewFailures;
    private final TestExecutionRepository executionRepo;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> passRateGauges = new ConcurrentHashMap<>();

    public QualityGateEvaluator(
            @Value("${platform.analytics.gate.min-pass-rate:0.80}") double minPassRate,
            @Value("${platform.analytics.gate.max-new-failures:10}") int maxNewFailures,
            TestExecutionRepository executionRepo,
            MeterRegistry meterRegistry) {
        this.minPassRate    = minPassRate;
        this.maxNewFailures = maxNewFailures;
        this.executionRepo  = executionRepo;
        this.meterRegistry  = meterRegistry;
    }

    /**
     * Evaluates quality gates for an incoming result. {@code projectId} is the resolved
     * UUID of the project this result belongs to.
     */
    @Transactional(readOnly = true)
    public QualityGateResult evaluate(UnifiedTestResult result, UUID projectId) {
        List<String> violations = new ArrayList<>();

        // 1. Pass rate in this run
        double passRate = result.total() > 0
                ? (double) result.passed() / result.total()
                : 1.0;
        if (passRate < minPassRate) {
            violations.add(String.format(
                    "Pass rate %.1f%% is below minimum %.1f%%",
                    passRate * 100, minPassRate * 100));
        }

        // 2. New failures: tests that are FAILED/BROKEN in this run
        long newFailures = result.testCases().stream()
                .filter(tc -> tc.status() == TestStatus.FAILED || tc.status() == TestStatus.BROKEN)
                .count();
        if (newFailures > maxNewFailures) {
            violations.add(String.format(
                    "New failure count %d exceeds maximum %d", newFailures, maxNewFailures));
        }

        // 3. Pass rate trend: compare to 7-day rolling average
        double rollingPassRate = computeRollingPassRate(projectId, 7);
        if (rollingPassRate > 0 && passRate < rollingPassRate * 0.90) {
            violations.add(String.format(
                    "Pass rate %.1f%% is more than 10%% below 7-day average %.1f%%",
                    passRate * 100, rollingPassRate * 100));
        }

        recordPassRateGauge(result, passRate);

        if (violations.isEmpty()) {
            return QualityGateResult.pass(passRate, (int) newFailures);
        }
        return QualityGateResult.fail(passRate, (int) newFailures, violations);
    }

    private void recordPassRateGauge(UnifiedTestResult result, double passRate) {
        String team    = result.teamId()    != null ? result.teamId()    : "unknown";
        String project = result.projectId() != null ? result.projectId() : "unknown";
        String branch  = result.branch()    != null ? result.branch()    : "unknown";
        String key = team + "/" + project + "/" + branch;
        AtomicLong backing = passRateGauges.computeIfAbsent(key, k -> {
            AtomicLong ref = new AtomicLong(0);
            Gauge.builder("platform.quality.gate.pass.rate", ref, r -> r.get() / 10000.0)
                    .description("Pass rate from the most recent quality gate evaluation (0.0–1.0)")
                    .tag("team", team)
                    .tag("project", project)
                    .tag("branch", branch)
                    .register(meterRegistry);
            return ref;
        });
        backing.set(Math.round(passRate * 10000));
    }

    private double computeRollingPassRate(UUID projectId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        Double rate = executionRepo.computePassRate(projectId, since);
        return rate != null ? rate : 0.0;
    }
}
