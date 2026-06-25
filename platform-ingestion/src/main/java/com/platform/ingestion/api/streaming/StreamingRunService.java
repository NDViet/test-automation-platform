package com.platform.ingestion.api.streaming;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.common.enums.TestType;
import com.platform.common.enums.TriggerType;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestExecutionRepository;
import com.platform.core.service.ExecutionPersistenceService;
import com.platform.ingestion.api.IngestResponse;
import com.platform.ingestion.publisher.ResultEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages streaming test run sessions.
 *
 * <p>Each test result is persisted to the DB immediately when it arrives, so the analytics service
 * can query and display results while the run is still in progress. The {@link TestExecution} is
 * created with status {@code RUNNING} on {@code startRun()} and updated to {@code COMPLETED} with
 * final counts on {@code finishRun()}.
 *
 * <p>Zombie executions (client died, network cut, {@code finishRun} never called) are recovered by
 * two mechanisms:
 *
 * <ol>
 *   <li><b>Startup sweep</b> — on {@link ApplicationReadyEvent}, every {@code RUNNING} execution in
 *       the DB without a matching in-memory session is finalized immediately. This covers server
 *       restarts while a run was in progress.
 *   <li><b>Inactivity timeout</b> — a scheduler fires every {@code platform.stream.zombie-check-ms}
 *       (default 5 min) and finalizes any in-memory session that has not received a {@code
 *       pushTest} call within {@code platform.stream.inactivity-timeout-ms} (default 30 min).
 * </ol>
 */
@Service
public class StreamingRunService {

  private static final Logger log = LoggerFactory.getLogger(StreamingRunService.class);

  /** How long a session may be idle before it is treated as a zombie. */
  @Value("${platform.stream.inactivity-timeout-ms:1800000}")
  private long inactivityTimeoutMs;

  private final Map<String, StreamRunState> sessions = new ConcurrentHashMap<>();
  private final ExecutionPersistenceService persistenceService;
  private final TestCaseResultRepository resultRepo;
  private final TestExecutionRepository executionRepo;
  private final ResultEventPublisher eventPublisher;

  public StreamingRunService(
      ExecutionPersistenceService persistenceService,
      TestCaseResultRepository resultRepo,
      TestExecutionRepository executionRepo,
      ResultEventPublisher eventPublisher) {
    this.persistenceService = persistenceService;
    this.resultRepo = resultRepo;
    this.executionRepo = executionRepo;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Opens a new streaming session and immediately persists a {@link TestExecution} with {@code
   * status=RUNNING} so the run is visible in analytics right away.
   */
  public StreamRunController.StartRunResponse startRun(StreamRunController.StartRunRequest req) {
    String runId = UUID.randomUUID().toString();

    TriggerType triggerType = mapTrigger(req.trigger());

    persistenceService.createStreamingExecution(
        runId,
        req.orgSlug(),
        req.projectSlug(),
        req.teamSlug(),
        req.areaSlug(),
        req.iterationPath(),
        req.branch(),
        req.environment(),
        req.commitSha(),
        req.ciRunUrl(),
        req.ciProvider(),
        triggerType,
        req.workflow(),
        Instant.now());

    sessions.put(
        runId,
        new StreamRunState(
            runId,
            req.orgSlug(),
            req.projectSlug(),
            req.teamSlug(),
            req.areaSlug(),
            req.iterationPath(),
            req.branch(),
            req.environment(),
            req.commitSha(),
            req.ciRunUrl(),
            req.ciProvider(),
            req.workflow(),
            req.trigger()));

    log.info(
        "[Stream] Run started runId={} org={} project={} team={} provider={} trigger={}",
        runId,
        req.orgSlug(),
        req.projectSlug(),
        req.teamSlug(),
        req.ciProvider(),
        req.trigger());
    return new StreamRunController.StartRunResponse(runId);
  }

  /**
   * Persists a single test result immediately. The dashboard shows it as soon as this call returns
   * — no need to wait for the whole suite to finish.
   *
   * <p>On retry (same testId pushed again), the existing row is updated in-place so that each test
   * has exactly one result row with its latest status.
   */
  @Transactional
  public void pushTest(String runId, StreamRunController.TestEventRequest req) {
    StreamRunState state = requireSession(runId);
    state.touchActivity();
    TestStatus status = mapStatus(req.status());
    String testId = req.testId() != null ? req.testId() : req.displayName();

    TestExecution execution =
        executionRepo
            .findByRunId(runId)
            .orElseThrow(
                () -> new IllegalStateException("Execution not found for runId: " + runId));

    // Upsert: update the existing row on retry so each testId has one canonical result.
    resultRepo
        .findFirstByExecution_RunIdAndTestId(runId, testId)
        .ifPresentOrElse(
            existing -> {
              existing.setStatus(status);
              existing.setDurationMs(req.durationMs());
              existing.setFailureMessage(req.failureMessage());
              existing.setStackTrace(req.stackTrace());
              existing.setRetryCount(req.retryCount());
              existing.setHasScreenshot(req.hasScreenshot());
              existing.setHasVideo(req.hasVideo());
              existing.setWorkerIndex(req.workerIndex());
              existing.setAnnotations(req.annotations());
              existing.setCoveredFiles(req.coveredFiles());
              existing.setCoveredComponents(req.coveredComponents());
              existing.setCoveredRoutes(req.coveredRoutes());
              existing.setLabels(req.labels());
              resultRepo.save(existing);
            },
            () ->
                resultRepo.save(
                    TestCaseResult.builder()
                        .execution(execution)
                        .testId(testId)
                        .displayName(req.displayName())
                        .className(req.suiteName())
                        .methodName(req.displayName())
                        .tags(req.tags() != null ? req.tags() : List.of())
                        .status(status)
                        .durationMs(req.durationMs())
                        .failureMessage(req.failureMessage())
                        .stackTrace(req.stackTrace())
                        .retryCount(req.retryCount())
                        .specFile(req.specFile())
                        .browser(req.browser())
                        .annotations(req.annotations())
                        .hasScreenshot(req.hasScreenshot())
                        .hasVideo(req.hasVideo())
                        .workerIndex(req.workerIndex())
                        .coveredFiles(req.coveredFiles())
                        .coveredComponents(req.coveredComponents())
                        .coveredRoutes(req.coveredRoutes())
                        .labels(req.labels())
                        .build()));

    log.debug("[Stream] Test pushed runId={} test={} status={}", runId, req.displayName(), status);
  }

  /**
   * Finalizes the run: writes final counts, marks the execution {@code COMPLETED}, then publishes a
   * Kafka event so analytics can compute alerts and quality gates.
   *
   * <p>Counts are read from the DB — not from in-memory counters — so they are accurate even if the
   * same testId was pushed multiple times (retries).
   */
  public IngestResponse finishRun(String runId) {
    StreamRunState state = sessions.remove(runId);
    if (state == null)
      throw new IllegalArgumentException("Unknown or already finished runId: " + runId);

    TestExecution execution = executionRepo.findByRunId(runId).orElseThrow();
    finalizeExecution(execution, state.getStartedAt(), state.getTeamSlug());

    // Re-read totals from the updated execution row so the response is accurate.
    int total = executionRepo.findByRunId(runId).map(TestExecution::getTotalTests).orElse(0);

    log.info("[Stream] Run finished runId={} total={}", runId, total);
    return new IngestResponse(runId, "ACCEPTED", total, "/api/v1/executions/" + runId);
  }

  /**
   * Keep-alive ping from the reporter. Resets the inactivity timer so a legitimately long-running
   * test (e.g. a 35-minute E2E scenario) is not mistaken for a zombie.
   */
  public void heartbeat(String runId) {
    requireSession(runId).touchActivity();
    log.debug("[Stream] Heartbeat runId={}", runId);
  }

  // ── Zombie recovery ────────────────────────────────────────────────────────

  /**
   * On startup: finalize every RUNNING execution that has no in-memory session. These are
   * executions from a previous server instance whose {@code finishRun} was never received (server
   * restarted, container replaced, etc.).
   */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void recoverOrphanedExecutions() {
    List<TestExecution> running = executionRepo.findByStatus("RUNNING");
    if (running.isEmpty()) return;

    log.info("[Stream] Startup sweep: found {} orphaned RUNNING execution(s)", running.size());
    for (TestExecution exec : running) {
      if (sessions.containsKey(exec.getRunId())) continue; // live session — skip
      try {
        finalizeExecution(exec, exec.getExecutedAt(), null);
        log.info("[Stream] Orphan finalized runId={}", exec.getRunId());
      } catch (Exception e) {
        log.warn(
            "[Stream] Failed to finalize orphan runId={}: {}", exec.getRunId(), e.getMessage());
      }
    }
  }

  /**
   * Periodic check: finalize in-memory sessions that have received no {@code pushTest} activity for
   * longer than {@code platform.stream.inactivity-timeout-ms}.
   *
   * <p>This handles the case where the client process died without calling {@code finishRun} but
   * the server was not restarted.
   */
  @Scheduled(fixedDelayString = "${platform.stream.zombie-check-ms:300000}")
  @Transactional
  public void expireInactiveSessions() {
    Instant cutoff = Instant.now().minusMillis(inactivityTimeoutMs);
    sessions
        .entrySet()
        .removeIf(
            entry -> {
              StreamRunState state = entry.getValue();
              if (!state.getLastActivityAt().isBefore(cutoff)) return false;

              log.warn(
                  "[Stream] Session timed out (inactive >{}s) runId={}",
                  inactivityTimeoutMs / 1000,
                  state.getRunId());
              try {
                TestExecution exec = executionRepo.findByRunId(state.getRunId()).orElse(null);
                if (exec != null)
                  finalizeExecution(exec, state.getStartedAt(), state.getTeamSlug());
              } catch (Exception e) {
                log.warn(
                    "[Stream] Failed to finalize timed-out session runId={}: {}",
                    state.getRunId(),
                    e.getMessage());
              }
              return true; // remove from map regardless of finalize success
            });
  }

  /**
   * Core finalize logic shared by normal {@code finishRun}, startup sweep, and inactivity timeout.
   * Counts results from the DB, updates the execution row, and publishes a Kafka event.
   */
  private void finalizeExecution(TestExecution execution, Instant startedAt, String teamSlug) {
    String runId = execution.getRunId();
    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

    Map<TestStatus, Integer> counts =
        resultRepo.countGroupByStatusForExecution(execution.getId()).stream()
            .collect(
                Collectors.toMap(row -> (TestStatus) row[0], row -> ((Long) row[1]).intValue()));
    int passed = counts.getOrDefault(TestStatus.PASSED, 0);
    int failed = counts.getOrDefault(TestStatus.FAILED, 0);
    int skipped = counts.getOrDefault(TestStatus.SKIPPED, 0);
    int broken = counts.getOrDefault(TestStatus.BROKEN, 0);
    int total = passed + failed + skipped + broken;

    persistenceService.finalizeStreamingExecution(
        runId, total, passed, failed, skipped, broken, durationMs);

    String orgSlug = execution.getProject().getOrganization().getSlug();
    String projectSlug = execution.getProject().getSlug();
    String areaSlug = execution.getAreaSlug();
    eventPublisher.publish(
        buildUnifiedResult(
            orgSlug,
            projectSlug,
            teamSlug,
            areaSlug,
            execution,
            total,
            passed,
            failed,
            skipped,
            broken,
            durationMs));
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private StreamRunState requireSession(String runId) {
    StreamRunState state = sessions.get(runId);
    if (state == null) throw new IllegalArgumentException("Unknown runId: " + runId);
    return state;
  }

  private UnifiedTestResult buildUnifiedResult(
      String orgSlug,
      String projectSlug,
      String teamSlug,
      String areaSlug,
      TestExecution exec,
      int total,
      int passed,
      int failed,
      int skipped,
      int broken,
      long durationMs) {
    List<TestCaseResultDto> cases =
        resultRepo.findByExecutionId(exec.getId()).stream()
            .map(
                r ->
                    TestCaseResultDto.basic(
                        r.getTestId(),
                        r.getDisplayName(),
                        r.getClassName(),
                        r.getMethodName(),
                        r.getTags(),
                        r.getStatus(),
                        r.getDurationMs(),
                        r.getFailureMessage(),
                        r.getStackTrace(),
                        r.getRetryCount(),
                        List.of()))
            .toList();

    TriggerType triggerType =
        exec.getTriggerType() != null ? exec.getTriggerType() : TriggerType.MANUAL;
    String ciProvider = exec.getCiProvider() != null ? exec.getCiProvider() : "unknown";
    String suiteName = exec.getSuiteName();

    return new UnifiedTestResult(
        exec.getRunId(),
        orgSlug,
        projectSlug,
        exec.getBranch(),
        exec.getEnvironment(),
        exec.getCommitSha(),
        triggerType,
        suiteName,
        exec.getCiRunUrl(),
        exec.getExecutedAt(),
        total,
        passed,
        failed,
        skipped,
        broken,
        durationMs,
        SourceFormat.PLATFORM_NATIVE,
        cases,
        ciProvider,
        0,
        "",
        TestType.FUNCTIONAL,
        null,
        teamSlug,
        areaSlug);
  }

  private static TestStatus mapStatus(String raw) {
    if (raw == null) return TestStatus.BROKEN;
    return switch (raw.toLowerCase()) {
      case "passed" -> TestStatus.PASSED;
      case "failed", "timedout" -> TestStatus.FAILED;
      case "skipped", "interrupted" -> TestStatus.SKIPPED;
      default -> TestStatus.BROKEN;
    };
  }

  /**
   * Maps a raw CI event name to {@link TriggerType}. GitHub: "push" → CI_PUSH,
   * "pull_request"/"pull_request_target" → CI_PR, "schedule" → SCHEDULED, "workflow_dispatch" →
   * MANUAL. GitLab: "push" → CI_PUSH, "merge_request_event" → CI_PR, "schedule" → SCHEDULED, "web"
   * / "api" → MANUAL.
   */
  static TriggerType mapTrigger(String trigger) {
    if (trigger == null) return TriggerType.MANUAL;
    return switch (trigger.toLowerCase()) {
      case "push" -> TriggerType.CI_PUSH;
      case "pull_request", "pull_request_target", "merge_request_event" -> TriggerType.CI_PR;
      case "schedule" -> TriggerType.SCHEDULED;
      default -> TriggerType.MANUAL;
    };
  }
}
