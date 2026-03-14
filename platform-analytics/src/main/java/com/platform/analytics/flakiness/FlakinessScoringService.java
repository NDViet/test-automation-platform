package com.platform.analytics.flakiness;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.TestCaseResultRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.DoubleStream;

/**
 * Computes and persists flakiness scores for individual tests using the formula:
 * <pre>
 *   score = (failure_rate * 0.5) + (recency_weight * 0.3) + (env_variance * 0.2)
 * </pre>
 *
 * Classification thresholds:
 * <ul>
 *   <li>&lt; 0.10  → STABLE</li>
 *   <li>0.10–0.30 → WATCH</li>
 *   <li>0.30–0.60 → FLAKY</li>
 *   <li>&gt; 0.60  → CRITICAL_FLAKY</li>
 * </ul>
 */
@Service
public class FlakinessScoringService {

    private static final Logger log = LoggerFactory.getLogger(FlakinessScoringService.class);

    static final int LOOKBACK_DAYS   = 30;
    static final double DECAY_FACTOR = 0.85; // per-day decay for recency weight
    static final int SCALE           = 4;

    private final TestCaseResultRepository resultRepo;
    private final FlakinessScoreRepository scoreRepo;

    public FlakinessScoringService(TestCaseResultRepository resultRepo,
                                   FlakinessScoreRepository scoreRepo,
                                   MeterRegistry meterRegistry) {
        this.resultRepo = resultRepo;
        this.scoreRepo  = scoreRepo;

        Gauge.builder("platform.flakiness.critical.count", scoreRepo,
                        r -> r.countByClassification(FlakinessScore.Classification.CRITICAL_FLAKY))
                .description("Number of tests classified as CRITICAL_FLAKY")
                .register(meterRegistry);

        Gauge.builder("platform.flakiness.watch.count", scoreRepo,
                        r -> r.countByClassification(FlakinessScore.Classification.WATCH))
                .description("Number of tests in the WATCH flakiness band")
                .register(meterRegistry);
    }

    /**
     * Computes the flakiness score for {@code testId} in {@code projectId}
     * and upserts the result. No-op if there is no run history.
     */
    @Transactional
    public void computeAndPersist(String testId, UUID projectId) {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<TestCaseResult> history =
                resultRepo.findWithExecutionByTestIdAndProjectIdSince(testId, projectId, since);

        if (history.isEmpty()) {
            log.debug("[Flakiness] No history for testId={} project={}", testId, projectId);
            return;
        }

        ScoringInput input = buildInput(history);
        double score = Math.min(1.0, Math.max(0.0,
                (input.failureRate() * 0.5)
                        + (input.recencyWeight() * 0.3)
                        + (input.envVariance() * 0.2)));

        FlakinessScore.Classification classification = classify(score);

        scoreRepo.upsert(
                testId,
                projectId,
                bd(score),
                classification.name(),
                input.total(),
                input.failures(),
                bd(input.failureRate()),
                input.lastFailedAt(),
                input.lastPassedAt()
        );

        log.debug("[Flakiness] testId={} score={} classification={}", testId, score, classification);
    }

    // visible for testing
    ScoringInput buildInput(List<TestCaseResult> history) {
        int total    = history.size();
        int failures = (int) history.stream().filter(this::isFailure).count();
        double failureRate = (double) failures / total;

        double recencyWeight = computeRecencyWeight(history);
        double envVariance   = computeEnvVariance(history);

        Instant lastFailed = history.stream().filter(this::isFailure)
                .map(TestCaseResult::getCreatedAt).max(Comparator.naturalOrder()).orElse(null);
        Instant lastPassed = history.stream()
                .filter(r -> r.getStatus() == TestStatus.PASSED)
                .map(TestCaseResult::getCreatedAt).max(Comparator.naturalOrder()).orElse(null);

        return new ScoringInput(total, failures, failureRate, recencyWeight, envVariance,
                lastFailed, lastPassed);
    }

    private double computeRecencyWeight(List<TestCaseResult> history) {
        Instant now = Instant.now();
        double weightedFailures = 0.0;
        double weightedTotal    = 0.0;
        for (TestCaseResult r : history) {
            double daysSince = Duration.between(r.getCreatedAt(), now).toMinutes() / 1440.0;
            double weight    = Math.pow(DECAY_FACTOR, daysSince);
            if (isFailure(r)) weightedFailures += weight;
            weightedTotal += weight;
        }
        return weightedTotal > 0 ? weightedFailures / weightedTotal : 0.0;
    }

    private double computeEnvVariance(List<TestCaseResult> history) {
        // Group by environment, compute failure rate per env, return stddev
        Map<String, long[]> envStats = new HashMap<>(); // env → [total, failures]
        for (TestCaseResult r : history) {
            String env = r.getExecution().getEnvironment();
            if (env == null) env = "unknown";
            envStats.computeIfAbsent(env, k -> new long[]{0L, 0L});
            envStats.get(env)[0]++;
            if (isFailure(r)) envStats.get(env)[1]++;
        }
        if (envStats.size() <= 1) return 0.0;

        double[] rates = envStats.values().stream()
                .mapToDouble(s -> (double) s[1] / s[0])
                .toArray();
        double mean     = DoubleStream.of(rates).average().orElse(0.0);
        double variance = DoubleStream.of(rates).map(r -> (r - mean) * (r - mean)).average().orElse(0.0);
        return Math.min(1.0, Math.sqrt(variance));
    }

    private boolean isFailure(TestCaseResult r) {
        return r.getStatus() == TestStatus.FAILED || r.getStatus() == TestStatus.BROKEN;
    }

    static FlakinessScore.Classification classify(double score) {
        if (score < 0.10) return FlakinessScore.Classification.STABLE;
        if (score < 0.30) return FlakinessScore.Classification.WATCH;
        if (score < 0.60) return FlakinessScore.Classification.FLAKY;
        return FlakinessScore.Classification.CRITICAL_FLAKY;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    record ScoringInput(
            int total,
            int failures,
            double failureRate,
            double recencyWeight,
            double envVariance,
            Instant lastFailedAt,
            Instant lastPassedAt
    ) {}
}
