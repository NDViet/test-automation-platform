# Skill: implement-flakiness-scorer

Implement or extend the flakiness scoring algorithm in `platform-analytics`.

## Context

- Module: `platform-analytics`
- Package: `com.platform.analytics.flakiness`
- Triggered by: Kafka consumer on `test.results.raw` topic after each run
- Persists to: `flakiness_scores` table (via `platform-core` repository)
- Score range: 0.0 (perfectly stable) to 1.0 (always fails)

## Scoring Formula

```
score = (failure_rate * 0.5) + (recency_weight * 0.3) + (env_variance * 0.2)

Where:
  failure_rate    = failed_runs / total_runs  (in lookback window, default 30 days)
  recency_weight  = weighted failure rate with daily decay factor 0.85
                    (recent failures count more than old ones)
  env_variance    = stddev(failure_rate per environment)
                    (fails in staging but not prod = more suspicious)

Classification:
  score < 0.10   → STABLE
  score 0.10–0.30 → WATCH
  score 0.30–0.60 → FLAKY
  score > 0.60   → CRITICAL_FLAKY
```

## Instructions

### 1. Read existing code first
- Read `platform-analytics/src/main/java/com/platform/analytics/flakiness/` fully
- Read `platform-core/src/main/java/com/platform/core/domain/FlakinessScore.java`
- Read `platform-core/src/main/java/com/platform/core/repository/FlakinessRepository.java`

### 2. Implement `FlakinessScoringService`
```java
@Service
public class FlakinessScoringService {

    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final double DECAY_FACTOR = 0.85;

    // Score thresholds
    public static final double WATCH_THRESHOLD    = 0.10;
    public static final double FLAKY_THRESHOLD    = 0.30;
    public static final double CRITICAL_THRESHOLD = 0.60;

    private final TestCaseHistoryRepository historyRepository;
    private final FlakinessRepository flakinessRepository;

    public void updateScore(String testId, UUID projectId) {
        Instant from = Instant.now().minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<TestCaseResult> history = historyRepository
            .findByTestIdAndProjectIdSince(testId, projectId, from);

        if (history.size() < 3) return;   // not enough data — skip scoring

        double score = computeScore(history);
        FlakinessClassification classification = classify(score);

        flakinessRepository.upsert(FlakinessScore.builder()
            .testId(testId)
            .projectId(projectId)
            .score(score)
            .classification(classification)
            .totalRuns(history.size())
            .failureCount((int) history.stream().filter(r -> r.getStatus() == FAILED).count())
            .failureRate(computeFailureRate(history))
            .lastFailedAt(lastOccurrence(history, FAILED))
            .lastPassedAt(lastOccurrence(history, PASSED))
            .computedAt(Instant.now())
            .build());
    }

    double computeScore(List<TestCaseResult> history) {
        double failureRate = computeFailureRate(history);
        double recencyWeight = computeRecencyWeight(history);
        double envVariance = computeEnvVariance(history);
        return (failureRate * 0.5) + (recencyWeight * 0.3) + (envVariance * 0.2);
    }

    private double computeRecencyWeight(List<TestCaseResult> history) {
        // Sort oldest to newest; weight each run, decay applied per day from now
        Instant now = Instant.now();
        double weightedFailures = 0.0;
        double totalWeight = 0.0;

        for (TestCaseResult r : history) {
            long daysAgo = ChronoUnit.DAYS.between(r.getCreatedAt(), now);
            double weight = Math.pow(DECAY_FACTOR, daysAgo);
            totalWeight += weight;
            if (r.getStatus() == FAILED) weightedFailures += weight;
        }
        return totalWeight == 0 ? 0 : weightedFailures / totalWeight;
    }

    private double computeEnvVariance(List<TestCaseResult> history) {
        // Group by environment, compute failure rate per env, return stddev
        Map<String, List<TestCaseResult>> byEnv = history.stream()
            .collect(Collectors.groupingBy(r -> r.getExecution().getEnvironment()));

        if (byEnv.size() < 2) return 0.0;   // single env — no variance possible

        double[] rates = byEnv.values().stream()
            .mapToDouble(this::computeFailureRate)
            .toArray();

        return computeStdDev(rates);
    }
}
```

### 3. Implement `FlakinessConsumer` (Kafka listener)
```java
@Component
public class FlakinessConsumer {

    @KafkaListener(topics = Topics.TEST_RESULTS_RAW, groupId = "platform-analytics-flakiness")
    public void onTestResultReceived(@Payload TestResultEvent event, Acknowledgment ack) {
        try {
            // Update score for every test case in the run that has a result
            event.testCases().stream()
                .filter(tc -> tc.status() == FAILED || tc.status() == PASSED)
                .forEach(tc -> scoringService.updateScore(tc.testId(), event.projectId()));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Flakiness scoring failed for runId={}", event.runId(), e);
            throw e;
        }
    }
}
```

### 4. Implement `FlakinessReportService` (query API)
```java
public record FlakinessReport(
    String testId,
    String displayName,
    double score,
    FlakinessClassification classification,
    int totalRuns,
    int failureCount,
    double failureRate,
    Instant lastFailedAt,
    String jiraTicketKey       // nullable
) {}

public List<FlakinessReport> getTopFlaky(UUID projectId, int limit) { ... }
public List<FlakinessReport> getTopFlakyForOrg(int limit) { ... }
public FlakinessReport getForTest(String testId, UUID projectId) { ... }
```

### 5. Write unit tests for the scoring algorithm
```java
class FlakinessScoringServiceTest {

    FlakinessScoringService service = new FlakinessScoringService(...);

    @Test
    void stableTestScoresBelowWatchThreshold() {
        // 1 failure in 20 runs, all recent, single env
        var history = buildHistory(20, 1, "staging");
        assertThat(service.computeScore(history)).isLessThan(WATCH_THRESHOLD);
    }

    @Test
    void consistentFailureClassifiedAsCriticalFlaky() {
        // 8 failures in 10 runs
        var history = buildHistory(10, 8, "staging");
        assertThat(service.computeScore(history)).isGreaterThan(CRITICAL_THRESHOLD);
    }

    @Test
    void envVarianceBoostsScore() {
        // Fails in staging, passes in prod → higher score than same overall rate in single env
        var mixed = buildHistoryMultiEnv(Map.of("staging", 0.5, "prod", 0.0));
        var single = buildHistory(20, 5, "staging");  // same 25% failure rate
        assertThat(service.computeScore(mixed)).isGreaterThan(service.computeScore(single));
    }

    @Test
    void recentFailuresScoreHigherThanOldFailures() {
        // Same count but recent vs old
        var recentFailures = buildHistoryWithDates(recent: 5 failures, old: 0 failures);
        var oldFailures    = buildHistoryWithDates(recent: 0 failures, old: 5 failures);
        assertThat(service.computeScore(recentFailures))
            .isGreaterThan(service.computeScore(oldFailures));
    }

    @Test
    void skipsTestsWithFewerThanThreeRuns() {
        service.updateScore("test-id", projectId);   // history has only 2 runs
        verify(flakinessRepository, never()).upsert(any());
    }
}
```

## Validation
- Score for a test that always fails = close to 1.0
- Score for a test that never fails = 0.0
- Score reflects recency (recent failures score higher than old ones at same rate)
- Score reflects env variance (staging-only failures score higher than uniform failures)
- Minimum 3 runs required before scoring starts
- Kafka consumer updates scores without blocking (non-overlapping by testId)
