package com.platform.ingestion.api.streaming;

import com.platform.common.enums.TestStatus;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight in-memory state for an open streaming run.
 *
 * <p>Test case results are now persisted to the DB immediately on each {@code pushTest()} call, so
 * this class only needs to track metadata (for the Kafka event at finishRun) and live counters (to
 * compute final totals without an extra DB round-trip).
 *
 * <p>Thread-safe: parallel Playwright workers can push results concurrently.
 */
public class StreamRunState {

  private final String runId;
  private final String orgSlug;
  private final String projectSlug;
  private final String teamSlug;
  private final String areaSlug;
  private final String branch;
  private final String environment;
  private final String commitSha;
  private final String ciRunUrl;
  private final String ciProvider;
  private final String workflow;
  private final String trigger;
  private final String iterationPath;
  private final Instant startedAt = Instant.now();
  private final AtomicReference<Instant> lastActivityAt = new AtomicReference<>(Instant.now());

  private final AtomicInteger total = new AtomicInteger();
  private final AtomicInteger passed = new AtomicInteger();
  private final AtomicInteger failed = new AtomicInteger();
  private final AtomicInteger skipped = new AtomicInteger();
  private final AtomicInteger broken = new AtomicInteger();

  public StreamRunState(
      String runId,
      String orgSlug,
      String projectSlug,
      String teamSlug,
      String areaSlug,
      String iterationPath,
      String branch,
      String environment,
      String commitSha,
      String ciRunUrl,
      String ciProvider,
      String workflow,
      String trigger) {
    this.runId = runId;
    this.orgSlug = orgSlug;
    this.projectSlug = projectSlug;
    this.teamSlug = teamSlug;
    this.areaSlug = areaSlug;
    this.iterationPath = iterationPath;
    this.branch = branch;
    this.environment = environment;
    this.commitSha = commitSha;
    this.ciRunUrl = ciRunUrl;
    this.ciProvider = ciProvider;
    this.workflow = workflow;
    this.trigger = trigger;
  }

  public void touchActivity() {
    lastActivityAt.set(Instant.now());
  }

  public void increment(TestStatus status) {
    total.incrementAndGet();
    switch (status) {
      case PASSED -> passed.incrementAndGet();
      case FAILED -> failed.incrementAndGet();
      case SKIPPED -> skipped.incrementAndGet();
      default -> broken.incrementAndGet();
    }
  }

  public String getRunId() {
    return runId;
  }

  public String getOrgSlug() {
    return orgSlug;
  }

  public String getProjectSlug() {
    return projectSlug;
  }

  public String getTeamSlug() {
    return teamSlug;
  }

  public String getAreaSlug() {
    return areaSlug;
  }

  public String getIterationPath() {
    return iterationPath;
  }

  public String getBranch() {
    return branch;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getCommitSha() {
    return commitSha;
  }

  public String getCiRunUrl() {
    return ciRunUrl;
  }

  public String getCiProvider() {
    return ciProvider;
  }

  public String getWorkflow() {
    return workflow;
  }

  public String getTrigger() {
    return trigger;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getLastActivityAt() {
    return lastActivityAt.get();
  }

  public int getTotal() {
    return total.get();
  }

  public int getPassed() {
    return passed.get();
  }

  public int getFailed() {
    return failed.get();
  }

  public int getSkipped() {
    return skipped.get();
  }

  public int getBroken() {
    return broken.get();
  }
}
