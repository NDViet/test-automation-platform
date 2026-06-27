package com.platform.ingestion.query;

import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.SotRelease;
import com.platform.core.domain.TestExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestExecutionRepository;
import com.platform.core.repository.TestRunRepository;
import com.platform.ingestion.query.dto.ExecutionDetailDto;
import com.platform.ingestion.query.dto.ExecutionSummaryDto;
import com.platform.ingestion.query.dto.TestCaseDto;
import com.platform.ingestion.query.dto.UnifiedExecutionItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExecutionQueryService {

  private final TestExecutionRepository executionRepo;
  private final TestCaseResultRepository testCaseRepo;
  private final TestRunRepository runRepo;
  private final TestCaseExecutionRepository tceRepo;
  private final AdoTeamRepository teamRepo;
  private final SotReleaseRepository releaseRepo;

  public ExecutionQueryService(
      TestExecutionRepository executionRepo,
      TestCaseResultRepository testCaseRepo,
      TestRunRepository runRepo,
      TestCaseExecutionRepository tceRepo,
      AdoTeamRepository teamRepo,
      SotReleaseRepository releaseRepo) {
    this.executionRepo = executionRepo;
    this.testCaseRepo = testCaseRepo;
    this.runRepo = runRepo;
    this.tceRepo = tceRepo;
    this.teamRepo = teamRepo;
    this.releaseRepo = releaseRepo;
  }

  public List<ExecutionSummaryDto> findByProject(UUID projectId, int limit) {
    return executionRepo
        .findByProjectIdOrderByExecutedAtDesc(projectId, PageRequest.of(0, limit))
        .stream()
        .map(ExecutionSummaryDto::from)
        .toList();
  }

  public Optional<ExecutionDetailDto> findByRunId(String runId) {
    return executionRepo
        .findByRunId(runId)
        .map(
            exec -> {
              List<TestCaseDto> cases =
                  testCaseRepo.findByExecutionId(exec.getId()).stream()
                      .map(TestCaseDto::from)
                      .toList();
              return new ExecutionDetailDto(ExecutionSummaryDto.from(exec), cases);
            });
  }

  /**
   * Unified list: merges manual {@code TestRun}s and automated {@code TestExecution}s, applies
   * scope filters, sorts newest-first, returns up to {@code limit} items.
   *
   * @param type "ALL" | "MANUAL" | "AUTOMATED"
   * @param teamId ADO team UUID string — applied to manual runs only
   * @param area ADO area path (manual) or area slug (automated)
   * @param iteration ADO iteration path — applied to manual runs only
   */
  public List<UnifiedExecutionItem> findUnified(
      UUID projectId, String type, String teamId, String area, String iteration, int limit) {

    List<UnifiedExecutionItem> result = new ArrayList<>();

    // ── MANUAL (TestRun) ──────────────────────────────────────────────────
    if (!"AUTOMATED".equalsIgnoreCase(type)) {
      List<TestRun> runs = runRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
      appendManualItems(runs, teamId, area, iteration, result);
    }

    // ── AUTOMATED (TestExecution) ─────────────────────────────────────────
    if (!"MANUAL".equalsIgnoreCase(type)) {
      List<TestExecution> execs =
          executionRepo
              .findByProjectIdOrderByExecutedAtDesc(projectId, PageRequest.of(0, 300))
              .getContent();
      appendAutomatedItems(execs, area, iteration, result);
    }

    result.sort(
        Comparator.comparing(
            UnifiedExecutionItem::date, Comparator.nullsLast(Comparator.reverseOrder())));
    return result.stream().limit(limit).toList();
  }

  // ── MANUAL mapping ────────────────────────────────────────────────────────

  private void appendManualItems(
      List<TestRun> runs,
      String teamId,
      String area,
      String iteration,
      List<UnifiedExecutionItem> target) {

    if (runs.isEmpty()) return;

    // Batch-load team names (one query for all distinct team IDs)
    Set<UUID> teamUuids =
        runs.stream().map(TestRun::getTeamId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<UUID, String> teamNames = batchTeamNames(teamUuids);

    // Batch-load release names (one query for all distinct release IDs)
    Set<UUID> releaseUuids =
        runs.stream()
            .map(TestRun::getReleaseId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, String> releaseNames = batchReleaseNames(releaseUuids);

    // Batch-load status counts for all runs (one GROUP BY query)
    List<UUID> runIds = runs.stream().map(TestRun::getId).collect(Collectors.toList());
    Map<UUID, long[]> countMap = buildCountsMap(runIds);

    for (TestRun run : runs) {
      if (teamId != null && !teamId.equals(uuidStr(run.getTeamId()))) continue;
      if (area != null && !area.equals(run.getAreaPath())) continue;
      if (iteration != null && !iteration.equals(run.getIterationPath())) continue;

      long[] c = countMap.getOrDefault(run.getId(), new long[6]);
      // c: [0]=total [1]=passed [2]=failed [3]=blocked [4]=skipped [5]=pending
      double passRate = c[0] > 0 ? (double) c[1] / c[0] : 0.0;

      target.add(
          new UnifiedExecutionItem(
              run.getId().toString(),
              "MANUAL",
              run.getName(),
              run.getStatus(),
              run.getEnvironment(),
              null,
              null,
              null,
              null,
              null,
              c[0],
              c[1],
              c[2],
              c[3],
              c[4],
              c[5],
              0L,
              passRate,
              0L,
              uuidStr(run.getTeamId()),
              lookup(teamNames, run.getTeamId()),
              run.getAreaPath(),
              run.getIterationPath(),
              uuidStr(run.getReleaseId()),
              lookup(releaseNames, run.getReleaseId()),
              run.getReleaseVersion(),
              run.getTriggeredBy(),
              null,
              run.getId().toString(),
              run.getCreatedAt()));
    }
  }

  /**
   * Links an automated {@code TestExecution} to a sprint/area for reporting purposes. The run
   * itself is immutable (results can't change), but its scope dimensions can be updated after the
   * fact so the execution shows up in sprint reports.
   */
  @Transactional
  public void updateScope(String runId, String iterationPath, String areaSlug) {
    executionRepo
        .findByRunId(runId)
        .ifPresent(
            exec -> {
              if (iterationPath != null) exec.setIterationPath(iterationPath);
              if (areaSlug != null) exec.setAreaSlug(areaSlug);
              executionRepo.save(exec);
            });
  }

  // ── AUTOMATED mapping ─────────────────────────────────────────────────────

  private void appendAutomatedItems(
      List<TestExecution> execs, String area, String iteration, List<UnifiedExecutionItem> target) {
    for (TestExecution exec : execs) {
      if (area != null && !area.equals(exec.getAreaSlug())) continue;
      if (iteration != null && !iteration.equals(exec.getIterationPath())) continue;

      double passRate =
          exec.getTotalTests() > 0 ? (double) exec.getPassed() / exec.getTotalTests() : 0.0;

      String workflow = exec.getSuiteName();
      String name =
          (workflow != null && !workflow.isBlank())
              ? workflow
              : buildAutoName(exec.getBranch(), exec.getCommitSha());

      String teamIdStr = exec.getTeam() != null ? exec.getTeam().getId().toString() : null;
      String teamNameStr = exec.getTeam() != null ? exec.getTeam().getName() : null;

      target.add(
          new UnifiedExecutionItem(
              exec.getId().toString(),
              "AUTOMATED",
              name,
              exec.getStatus(),
              exec.getEnvironment(),
              exec.getCiProvider(),
              exec.getBranch(),
              exec.getCommitSha(),
              workflow,
              exec.getTriggerType() != null ? exec.getTriggerType().name() : null,
              exec.getTotalTests(),
              exec.getPassed(),
              exec.getFailed(),
              0L,
              exec.getSkipped(),
              0L,
              exec.getBroken(),
              passRate,
              exec.getDurationMs() != null ? exec.getDurationMs() : 0L,
              teamIdStr,
              teamNameStr,
              exec.getAreaSlug(),
              exec.getIterationPath(),
              null,
              null,
              null,
              null,
              exec.getCiRunUrl(),
              exec.getRunId(),
              exec.getExecutedAt()));
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Map<UUID, long[]> buildCountsMap(Collection<UUID> runIds) {
    if (runIds.isEmpty()) return Map.of();
    Map<UUID, long[]> map = new HashMap<>();
    for (Object[] row : tceRepo.countByRunIdsGrouped(runIds)) {
      UUID rid = (UUID) row[0];
      String stat = (String) row[1];
      long cnt = (Long) row[2];
      long[] arr = map.computeIfAbsent(rid, k -> new long[6]);
      arr[0] += cnt;
      switch (stat) {
        case "PASSED" -> arr[1] += cnt;
        case "FAILED" -> arr[2] += cnt;
        case "BLOCKED" -> arr[3] += cnt;
        case "SKIPPED" -> arr[4] += cnt;
        case "PENDING" -> arr[5] += cnt;
      }
    }
    return map;
  }

  private Map<UUID, String> batchTeamNames(Set<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return teamRepo.findAllById(ids).stream()
        .collect(Collectors.toMap(AdoTeam::getId, t -> t.getName() != null ? t.getName() : ""));
  }

  private Map<UUID, String> batchReleaseNames(Set<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return releaseRepo.findAllById(ids).stream()
        .collect(Collectors.toMap(SotRelease::getId, r -> r.getName() != null ? r.getName() : ""));
  }

  private static String uuidStr(UUID id) {
    return id != null ? id.toString() : null;
  }

  /** Null-safe lookup — immutable maps (Map.of) throw on get(null), so guard the key. */
  private static <K> String lookup(java.util.Map<K, String> map, K key) {
    return key == null ? null : map.get(key);
  }

  private static String buildAutoName(String branch, String sha) {
    String b = (branch != null && !branch.isBlank()) ? branch : "unknown";
    String s = (sha != null && sha.length() >= 7) ? sha.substring(0, 7) : (sha != null ? sha : "");
    return s.isBlank() ? b : b + " · " + s;
  }
}
