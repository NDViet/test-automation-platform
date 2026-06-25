package com.platform.core.service;

import com.platform.common.dto.PerformanceMetricsDto;
import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestType;
import com.platform.common.enums.TriggerType;
import com.platform.core.domain.Organization;
import com.platform.core.domain.PerformanceMetric;
import com.platform.core.domain.Project;
import com.platform.core.domain.Team;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.PerformanceMetricRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestExecutionRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(ExecutionPersistenceService.class);

  private final TestExecutionRepository executionRepository;
  private final TestCaseResultRepository testCaseResultRepository;
  private final OrganizationRepository organizationRepository;
  private final ProjectRepository projectRepository;
  private final TeamRepository teamRepository;
  private final PerformanceMetricRepository performanceMetricRepository;

  public ExecutionPersistenceService(
      TestExecutionRepository executionRepository,
      TestCaseResultRepository testCaseResultRepository,
      OrganizationRepository organizationRepository,
      ProjectRepository projectRepository,
      TeamRepository teamRepository,
      PerformanceMetricRepository performanceMetricRepository) {
    this.executionRepository = executionRepository;
    this.testCaseResultRepository = testCaseResultRepository;
    this.organizationRepository = organizationRepository;
    this.projectRepository = projectRepository;
    this.teamRepository = teamRepository;
    this.performanceMetricRepository = performanceMetricRepository;
  }

  /**
   * Creates a {@link TestExecution} immediately when a streaming run opens. Counts are all zero;
   * status is "RUNNING". Call {@link #finalizeStreamingExecution} when the run closes to write
   * final totals and flip the status to "COMPLETED".
   */
  @Transactional
  public TestExecution createStreamingExecution(
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
      TriggerType triggerType,
      String workflow,
      Instant executedAt) {

    var org = findOrCreateOrganization(orgSlug);
    var project = findOrCreateProject(org, projectSlug);
    var team = teamSlug != null ? findOrCreateTeam(project, teamSlug) : null;

    var execution =
        TestExecution.builder()
            .runId(runId)
            .project(project)
            .team(team)
            .areaSlug(areaSlug)
            .iterationPath(iterationPath)
            .branch(branch != null ? branch : "unknown")
            .environment(environment != null ? environment : "unknown")
            .commitSha(commitSha)
            .ciRunUrl(ciRunUrl)
            .ciProvider(ciProvider)
            .triggerType(triggerType != null ? triggerType : TriggerType.MANUAL)
            .suiteName(workflow)
            .sourceFormat(SourceFormat.PLATFORM_NATIVE)
            .testType(TestType.FUNCTIONAL)
            .executedAt(executedAt)
            .status("RUNNING")
            .build();
    return executionRepository.save(execution);
  }

  /**
   * Updates count fields and marks a streaming execution as "COMPLETED". Called by {@code
   * StreamingRunService.finishRun()} after all test events are persisted.
   */
  @Transactional
  public void finalizeStreamingExecution(
      String runId, int total, int passed, int failed, int skipped, int broken, long durationMs) {

    executionRepository
        .findByRunId(runId)
        .ifPresent(
            e -> {
              e.setTotalTests(total);
              e.setPassed(passed);
              e.setFailed(failed);
              e.setSkipped(skipped);
              e.setBroken(broken);
              e.setDurationMs(durationMs);
              e.setStatus("COMPLETED");
              executionRepository.save(e);
            });
  }

  @Transactional
  public TestExecution persist(UnifiedTestResult result) {
    if (executionRepository.existsByRunId(result.runId())) {
      log.warn("Duplicate run detected runId={} — skipping persistence", result.runId());
      return executionRepository.findByRunId(result.runId()).orElseThrow();
    }

    // ADO-first hierarchy: Organisation → Project → Team (optional)
    var org = findOrCreateOrganization(result.teamId());
    var project = findOrCreateProject(org, result.projectId());
    var team = result.teamSlug() != null ? findOrCreateTeam(project, result.teamSlug()) : null;

    var execution = buildExecution(result, project, team);
    execution = executionRepository.save(execution);

    persistTestCases(execution, result);
    persistPerformanceMetrics(execution, result);

    log.info(
        "Persisted execution runId={} org={} project={} team={} testType={} total={} failed={}",
        result.runId(),
        result.teamId(),
        result.projectId(),
        result.teamSlug(),
        execution.getTestType(),
        result.total(),
        result.failed());

    return execution;
  }

  // ── Auto-registration ─────────────────────────────────────────────────────

  /**
   * Returns the team with the given slug, creating it on first encounter. The catch handles the
   * rare race where two concurrent results register the same new team — the loser re-fetches the
   * winner's row.
   */
  private Organization findOrCreateOrganization(String slug) {
    return organizationRepository
        .findBySlug(slug)
        .orElseGet(
            () -> {
              log.info("[Platform] Auto-registering new organization slug={}", slug);
              try {
                return organizationRepository.save(new Organization(toDisplayName(slug), slug));
              } catch (DataIntegrityViolationException e) {
                return organizationRepository.findBySlug(slug).orElseThrow();
              }
            });
  }

  private Project findOrCreateProject(Organization org, String slug) {
    return projectRepository
        .findByOrganizationIdAndSlug(org.getId(), slug)
        .orElseGet(
            () -> {
              log.info(
                  "[Platform] Auto-registering new project slug={} orgSlug={}",
                  slug,
                  org.getSlug());
              try {
                return projectRepository.save(new Project(org, toDisplayName(slug), slug));
              } catch (DataIntegrityViolationException e) {
                return projectRepository
                    .findByOrganizationIdAndSlug(org.getId(), slug)
                    .orElseThrow();
              }
            });
  }

  private Team findOrCreateTeam(Project project, String slug) {
    return teamRepository
        .findByProjectIdAndSlug(project.getId(), slug)
        .orElseGet(
            () -> {
              log.info(
                  "[Platform] Auto-registering new team slug={} projectId={}",
                  slug,
                  project.getId());
              try {
                return teamRepository.save(new Team(project.getId(), toDisplayName(slug), slug));
              } catch (DataIntegrityViolationException e) {
                return teamRepository.findByProjectIdAndSlug(project.getId(), slug).orElseThrow();
              }
            });
  }

  /** "team-saucedemo" → "Team Saucedemo", "proj-the-internet" → "Proj The Internet" */
  private static String toDisplayName(String slug) {
    StringBuilder sb = new StringBuilder();
    for (String word : slug.split("[-_]")) {
      if (word.isEmpty()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(word.charAt(0)));
      sb.append(word.substring(1));
    }
    return sb.toString();
  }

  private TestExecution buildExecution(UnifiedTestResult r, Project project, Team team) {
    return TestExecution.builder()
        .runId(r.runId())
        .project(project)
        .team(team)
        .areaSlug(r.areaSlug())
        .branch(r.branch())
        .commitSha(r.commitSha())
        .environment(r.environment())
        .triggerType(r.triggerType())
        .sourceFormat(r.sourceFormat())
        .testType(r.testType()) // explicit; falls back to TestType.from(sourceFormat) if null
        .ciProvider(r.ciProvider())
        .ciRunUrl(r.ciRunUrl())
        .totalTests(r.total())
        .passed(r.passed())
        .failed(r.failed())
        .skipped(r.skipped())
        .broken(r.broken())
        .durationMs(r.durationMs())
        .executionMode(r.executionMode())
        .parallelism(r.parallelism())
        .suiteName(r.suiteName())
        .executedAt(r.executedAt())
        .build();
  }

  private void persistPerformanceMetrics(TestExecution execution, UnifiedTestResult result) {
    if (result.testType() != TestType.PERFORMANCE) return;
    PerformanceMetricsDto dto = result.performanceMetrics();
    if (dto == null) return;

    var pm =
        new PerformanceMetric(
            execution,
            dto.avgMs(),
            dto.minMs(),
            dto.medianMs(),
            dto.maxMs(),
            dto.p90Ms(),
            dto.p95Ms(),
            dto.p99Ms(),
            dto.requestsTotal(),
            dto.requestsPerSecond(),
            dto.errorRate(),
            dto.vusMax(),
            dto.durationMs());
    performanceMetricRepository.save(pm);

    log.info(
        "Persisted performance metrics runId={} p90={}ms p95={}ms errorRate={} vusMax={}",
        result.runId(),
        dto.p90Ms(),
        dto.p95Ms(),
        dto.errorRate(),
        dto.vusMax());
  }

  private void persistTestCases(TestExecution execution, UnifiedTestResult result) {
    for (TestCaseResultDto dto : result.testCases()) {
      var tcr =
          TestCaseResult.builder()
              .execution(execution)
              .testId(dto.testId())
              .displayName(dto.displayName())
              .className(dto.className())
              .methodName(dto.methodName())
              .tags(dto.tags())
              .status(dto.status())
              .durationMs(dto.durationMs())
              .failureMessage(dto.failureMessage())
              .stackTrace(dto.stackTrace())
              .retryCount(dto.retryCount())
              .build();
      testCaseResultRepository.save(tcr);
    }
  }
}
