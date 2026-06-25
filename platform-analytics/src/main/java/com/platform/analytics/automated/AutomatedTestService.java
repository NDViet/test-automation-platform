package com.platform.analytics.automated;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.TestCaseResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AutomatedTestService {

  private static final int FLAKY_MIN_RUNS = 3;
  private static final int RECENT_RUNS_LIMIT = 15;

  private final TestCaseResultRepository resultRepo;

  public AutomatedTestService(TestCaseResultRepository resultRepo) {
    this.resultRepo = resultRepo;
  }

  /**
   * Returns per-test aggregated summaries for all tests seen in the project within {@code days}.
   *
   * @param status "ALL" | "PASSED" | "FAILED" | "FLAKY" | "SKIPPED"
   * @param search substring match on displayName, testId, suiteName, or specFile
   * @param tags OR filter: at least one tag must match
   * @param browsers OR filter: at least one browser must match
   * @param annotationTypes OR filter: at least one annotation type must match (fixme, slow, …)
   * @param labelKey label key to match (paired with labelValue); ignored when blank
   * @param labelValue label value to match for labelKey; when blank, any value matches
   * @param specFile prefix match on the spec file path (e.g. "tests/checkout/")
   */
  public List<AutomatedTestSummaryDto> getSummaries(
      UUID projectId,
      int days,
      String search,
      String status,
      List<String> tags,
      List<String> browsers,
      List<String> annotationTypes,
      String labelKey,
      String labelValue,
      String specFile) {

    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    List<TestCaseResult> all = resultRepo.findByProjectSince(projectId, since);

    // Group by testId — results are already sorted DESC by createdAt from the query
    Map<String, List<TestCaseResult>> byTest =
        all.stream()
            .collect(
                Collectors.groupingBy(
                    TestCaseResult::getTestId, LinkedHashMap::new, Collectors.toList()));

    return byTest.entrySet().stream()
        .map(e -> toSummary(e.getKey(), e.getValue()))
        .filter(dto -> matchesSearch(dto, search))
        .filter(dto -> matchesStatus(dto, status))
        .filter(dto -> matchesTags(dto, tags))
        .filter(dto -> matchesBrowsers(dto, browsers))
        .filter(dto -> matchesAnnotationTypes(dto, annotationTypes))
        .filter(dto -> matchesLabel(dto, labelKey, labelValue))
        .filter(dto -> matchesSpecFile(dto, specFile))
        .sorted(Comparator.comparing(AutomatedTestSummaryDto::lastRunAt).reversed())
        .toList();
  }

  /** Distinct sorted tags seen in the project within {@code days}. */
  public List<String> getProjectTags(UUID projectId, int days) {
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    return resultRepo.findByProjectSince(projectId, since).stream()
        .filter(r -> r.getTags() != null)
        .flatMap(r -> r.getTags().stream())
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  /** Distinct sorted Playwright project names (browsers/devices) seen within {@code days}. */
  public List<String> getProjectBrowsers(UUID projectId, int days) {
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    return resultRepo.findByProjectSince(projectId, since).stream()
        .map(TestCaseResult::getBrowser)
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  /** Distinct sorted annotation types (fixme, slow, fail, …) seen within {@code days}. */
  public List<String> getProjectAnnotationTypes(UUID projectId, int days) {
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    return resultRepo.findByProjectSince(projectId, since).stream()
        .filter(r -> r.getAnnotations() != null)
        .flatMap(r -> r.getAnnotations().stream())
        .map(a -> a.get("type"))
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  /** Distinct sorted label keys (owner, jira, team, …) seen within {@code days}. */
  public List<String> getProjectLabelKeys(UUID projectId, int days) {
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    return resultRepo.findByProjectSince(projectId, since).stream()
        .filter(r -> r.getLabels() != null)
        .flatMap(r -> r.getLabels().stream())
        .map(l -> l.get("key"))
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  /** Distinct sorted values for a specific label key within {@code days}. */
  public List<String> getProjectLabelValues(UUID projectId, int days, String labelKey) {
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    return resultRepo.findByProjectSince(projectId, since).stream()
        .filter(r -> r.getLabels() != null)
        .flatMap(r -> r.getLabels().stream())
        .filter(l -> labelKey.equals(l.get("key")) && l.get("value") != null)
        .map(l -> l.get("value"))
        .distinct()
        .sorted()
        .toList();
  }

  /** Per-day execution trend + last N individual runs for a single test. */
  public AutomatedTestDetailDto getDetail(UUID projectId, String testId, int days) {
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    List<TestCaseResult> results =
        resultRepo.findByTestIdAndProjectIdSince(testId, projectId, since);

    List<TestTrendPointDto> trend = buildDailyTrend(results, since, days);

    List<RecentRunDto> recentRuns =
        results.stream().limit(RECENT_RUNS_LIMIT).map(this::toRecentRun).toList();

    return new AutomatedTestDetailDto(trend, recentRuns);
  }

  // ── Aggregation helpers ───────────────────────────────────────────────────

  private AutomatedTestSummaryDto toSummary(String testId, List<TestCaseResult> results) {
    // results are sorted DESC by createdAt — index 0 is the latest
    TestCaseResult latest = results.get(0);
    TestExecution latestExec = latest.getExecution();

    long total = results.size();
    long passed = count(results, TestStatus.PASSED);
    long failed = count(results, TestStatus.FAILED);
    long skipped = count(results, TestStatus.SKIPPED);
    long broken = count(results, TestStatus.BROKEN);

    double passRate = total > 0 ? (double) passed / total : 0.0;
    double failRate = total > 0 ? (double) (failed + broken) / total : 0.0;
    double avgMs =
        results.stream()
            .mapToLong(r -> r.getDurationMs() != null ? r.getDurationMs() : 0L)
            .average()
            .orElse(0.0);

    List<String> tags =
        results.stream()
            .filter(r -> r.getTags() != null)
            .flatMap(r -> r.getTags().stream())
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    // specFile is stable per test — take from latest (falls back through history if latest is null)
    String specFile =
        results.stream()
            .map(TestCaseResult::getSpecFile)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    // Distinct browsers (Playwright project names) across all results in the window
    List<String> browsers =
        results.stream()
            .map(TestCaseResult::getBrowser)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();

    // Distinct annotation types (fixme, slow, fail, skip, custom)
    List<String> annotationTypes =
        results.stream()
            .filter(r -> r.getAnnotations() != null)
            .flatMap(r -> r.getAnnotations().stream())
            .map(a -> a.get("type"))
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();

    // Label map: key → distinct sorted values across all results
    Map<String, List<String>> labelMap =
        results.stream()
            .filter(r -> r.getLabels() != null)
            .flatMap(r -> r.getLabels().stream())
            .filter(l -> l.get("key") != null && l.get("value") != null)
            .collect(
                Collectors.groupingBy(
                    l -> l.get("key"),
                    TreeMap::new,
                    Collectors.collectingAndThen(
                        Collectors.mapping(l -> l.get("value"), Collectors.toList()),
                        list -> list.stream().distinct().sorted().toList())));

    boolean hasScreenshot = results.stream().anyMatch(TestCaseResult::isHasScreenshot);
    boolean hasVideo = results.stream().anyMatch(TestCaseResult::isHasVideo);

    return new AutomatedTestSummaryDto(
        testId,
        latest.getDisplayName(),
        latest.getClassName(),
        tags,
        total,
        passed,
        failed,
        skipped,
        broken,
        passRate,
        failRate,
        latest.getStatus().name(),
        latestExec != null ? latestExec.getRunId() : null,
        latest.getCreatedAt(),
        avgMs,
        specFile,
        browsers,
        annotationTypes,
        labelMap,
        hasScreenshot,
        hasVideo);
  }

  private List<TestTrendPointDto> buildDailyTrend(
      List<TestCaseResult> results, Instant since, int days) {
    Map<LocalDate, List<TestCaseResult>> byDay =
        results.stream()
            .collect(
                Collectors.groupingBy(r -> r.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()));

    LocalDate start = since.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate end = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();

    List<TestTrendPointDto> trend = new ArrayList<>();
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      List<TestCaseResult> dayResults = byDay.getOrDefault(d, List.of());
      if (dayResults.isEmpty()) continue;
      int total = dayResults.size();
      int pass = (int) count(dayResults, TestStatus.PASSED);
      int fail =
          (int) (count(dayResults, TestStatus.FAILED) + count(dayResults, TestStatus.BROKEN));
      int skip = (int) count(dayResults, TestStatus.SKIPPED);
      double rate = total > 0 ? (double) pass / total : 0.0;
      long avgMs =
          (long)
              dayResults.stream()
                  .mapToLong(r -> r.getDurationMs() != null ? r.getDurationMs() : 0L)
                  .average()
                  .orElse(0.0);
      trend.add(new TestTrendPointDto(d.toString(), total, pass, fail, skip, rate, avgMs));
    }
    return trend;
  }

  private RecentRunDto toRecentRun(TestCaseResult r) {
    TestExecution exec = r.getExecution();
    return new RecentRunDto(
        exec != null ? exec.getRunId() : null,
        r.getId(),
        r.getStatus().name(),
        r.getCreatedAt(),
        r.getDurationMs(),
        r.getFailureMessage(),
        exec != null ? exec.getEnvironment() : null,
        exec != null ? exec.getBranch() : null,
        r.getTraceStorePath() != null,
        r.getBrowser(),
        r.getSpecFile(),
        r.isHasScreenshot(),
        r.isHasVideo());
  }

  // ── Filters ───────────────────────────────────────────────────────────────

  private boolean matchesSearch(AutomatedTestSummaryDto dto, String search) {
    if (search == null || search.isBlank()) return true;
    String q = search.toLowerCase();
    return (dto.testId() != null && dto.testId().toLowerCase().contains(q))
        || (dto.displayName() != null && dto.displayName().toLowerCase().contains(q))
        || (dto.suiteName() != null && dto.suiteName().toLowerCase().contains(q))
        || (dto.specFile() != null && dto.specFile().toLowerCase().contains(q));
  }

  private boolean matchesTags(AutomatedTestSummaryDto dto, List<String> tags) {
    if (tags == null || tags.isEmpty()) return true;
    return dto.tags().stream().anyMatch(tags::contains);
  }

  private boolean matchesStatus(AutomatedTestSummaryDto dto, String status) {
    if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) return true;
    return switch (status.toUpperCase()) {
      case "PASSED" -> "PASSED".equals(dto.lastStatus());
      case "FAILED" -> "FAILED".equals(dto.lastStatus()) || "BROKEN".equals(dto.lastStatus());
      case "SKIPPED" -> "SKIPPED".equals(dto.lastStatus());
      case "FLAKY" ->
          dto.totalRuns() >= FLAKY_MIN_RUNS && dto.passRate() > 0.0 && dto.passRate() < 1.0;
      default -> true;
    };
  }

  private boolean matchesBrowsers(AutomatedTestSummaryDto dto, List<String> browsers) {
    if (browsers == null || browsers.isEmpty()) return true;
    return dto.browsers().stream().anyMatch(browsers::contains);
  }

  private boolean matchesAnnotationTypes(
      AutomatedTestSummaryDto dto, List<String> annotationTypes) {
    if (annotationTypes == null || annotationTypes.isEmpty()) return true;
    return dto.annotationTypes().stream().anyMatch(annotationTypes::contains);
  }

  private boolean matchesLabel(AutomatedTestSummaryDto dto, String labelKey, String labelValue) {
    if (labelKey == null || labelKey.isBlank()) return true;
    List<String> values = dto.labelMap().get(labelKey);
    if (values == null || values.isEmpty()) return false;
    return labelValue == null || labelValue.isBlank() || values.contains(labelValue);
  }

  private boolean matchesSpecFile(AutomatedTestSummaryDto dto, String specFile) {
    if (specFile == null || specFile.isBlank()) return true;
    return dto.specFile() != null && dto.specFile().startsWith(specFile);
  }

  private static long count(List<TestCaseResult> list, TestStatus status) {
    return list.stream().filter(r -> r.getStatus() == status).count();
  }
}
