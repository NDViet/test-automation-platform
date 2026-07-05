package com.platform.ingestion.management.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.RunSchedule;
import com.platform.core.repository.RunScheduleRepository;
import com.platform.ingestion.management.ProjectGitHubWorkflowService;
import com.platform.ingestion.management.ProjectGitHubWorkflowService.DispatchRequest;
import com.platform.ingestion.management.ProjectGitHubWorkflowService.DispatchResult;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires due CI run schedules. A poller ({@code @Scheduled}) selects enabled schedules whose {@code
 * next_run_at} is due and dispatches each via the existing GitHub {@code workflow_dispatch}
 * integration; {@code next_run_at} is always advanced after an attempt so a failing workflow
 * doesn't hammer GitHub every tick.
 *
 * <p><b>Single-dispatcher assumption:</b> there is no distributed lock (no ShedLock). In a
 * multi-instance deployment this poller can double-dispatch — run it on a single instance, or add a
 * lock before scaling out.
 */
@Component
public class RunScheduleDispatcher {

  private static final Logger log = LoggerFactory.getLogger(RunScheduleDispatcher.class);

  private final RunScheduleRepository repo;
  private final ProjectGitHubWorkflowService github;
  private final ObjectMapper om;

  public RunScheduleDispatcher(
      RunScheduleRepository repo, ProjectGitHubWorkflowService github, ObjectMapper om) {
    this.repo = repo;
    this.github = github;
    this.om = om;
  }

  @Scheduled(fixedDelayString = "${platform.schedule.poll-ms:60000}")
  public void pollAndDispatch() {
    for (RunSchedule s : repo.findDueEnabled(Instant.now())) {
      dispatch(s);
    }
  }

  /** Dispatches one schedule now and advances its next run. Never throws (failures are logged). */
  @Transactional
  public DispatchResult dispatch(RunSchedule s) {
    DispatchResult result;
    try {
      result =
          github.triggerDispatch(
              s.getProjectId(),
              new DispatchRequest(
                  s.getRepoFullName(), s.getWorkflowId(), s.getRef(), parseInputs(s.getInputs())));
      log.info(
          "[Schedule] dispatched {} (workflow {}) → {}",
          s.getId(),
          s.getWorkflowId(),
          result.message());
    } catch (Exception e) {
      log.warn("[Schedule] dispatch failed for {}: {}", s.getId(), e.getMessage());
      result = new DispatchResult(false, e.getMessage());
    }
    // Advance regardless of outcome so a broken schedule doesn't retry every tick.
    s.markDispatched(Instant.now());
    repo.save(s);
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> parseInputs(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return om.readValue(json, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }
}
