package com.platform.ingestion.management.tcm;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.SotRelease;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.TestRunRepository;
import com.platform.core.service.MatrixType;
import com.platform.core.service.TcmRunService;
import com.platform.core.service.ado.AzureBoardsPollClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TestRunService {

  private final TestRunRepository runRepo;
  private final TestCaseExecutionRepository execRepo;
  private final PlatformTestCaseRepository testCaseRepo;
  private final TcmRunService tcmRunService;
  private final SotReleaseRepository releaseRepo;
  private final AdoTeamRepository teamRepo;
  private final SuiteResolverService suiteResolver;
  private final com.platform.core.repository.TestSuiteRepository suiteRepo;
  private final AzureBoardsPollClient adoClient;

  public TestRunService(
      TestRunRepository runRepo,
      TestCaseExecutionRepository execRepo,
      PlatformTestCaseRepository testCaseRepo,
      TcmRunService tcmRunService,
      SotReleaseRepository releaseRepo,
      AdoTeamRepository teamRepo,
      SuiteResolverService suiteResolver,
      com.platform.core.repository.TestSuiteRepository suiteRepo,
      AzureBoardsPollClient adoClient) {
    this.runRepo = runRepo;
    this.execRepo = execRepo;
    this.testCaseRepo = testCaseRepo;
    this.tcmRunService = tcmRunService;
    this.releaseRepo = releaseRepo;
    this.teamRepo = teamRepo;
    this.suiteResolver = suiteResolver;
    this.suiteRepo = suiteRepo;
    this.adoClient = adoClient;
  }

  @Transactional(readOnly = true)
  public List<TestRunDto> list(UUID projectId) {
    return runRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .map(run -> toDto(run))
        .toList();
  }

  @Transactional(readOnly = true)
  public TestRunDto get(UUID projectId, UUID runId) {
    TestRun run = loadAndVerify(projectId, runId);
    return toDto(run);
  }

  public TestRunDto create(UUID projectId, CreateTestRunRequest req) {
    // Non-empty runs go through TcmRunService: enforces the confirmed gate
    // (APPROVED-only) and expands each case's property matrix (Full/Pairwise)
    // into per-combination executions under the chosen Environment.
    UUID releaseId = parseUuid(req.releaseId());
    UUID teamId = parseUuid(req.teamId());
    String iterationPath = req.iterationPath();
    String areaPath = req.areaPath();

    // When a Release is chosen, the run inherits the release's composite scope for
    // any dimension the caller left blank (release = iteration ∧ area ∧ team).
    if (releaseId != null) {
      SotRelease rel =
          releaseRepo
              .findById(releaseId)
              .filter(r -> projectId.equals(r.getProjectId()))
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "Release not found in project: " + releaseId));
      if (isBlank(iterationPath)) iterationPath = rel.getMapIterationPath();
      if (isBlank(areaPath)) areaPath = rel.getMapAreaPath();
      if (teamId == null) teamId = rel.getMapTeamId();
    }

    // Resolve any selected suites and union their cases with explicitly-picked ones.
    List<String> suiteIds = req.suiteIds();
    if (suiteIds != null && !suiteIds.isEmpty()) {
      List<UUID> sids = suiteIds.stream().map(UUID::fromString).toList();
      // A single suite also lends its Area/Team scope when the run left it blank.
      if (sids.size() == 1) {
        var suite = suiteRepo.findByProjectIdAndId(projectId, sids.get(0)).orElse(null);
        if (suite != null) {
          if (isBlank(areaPath)) areaPath = suite.getAreaPath();
          if (teamId == null) teamId = suite.getTeamId();
          if (isBlank(iterationPath)) iterationPath = suite.getFilterIteration();
        }
      }
    }
    List<UUID> caseIds = effectiveCaseIds(projectId, req.testCaseIds(), suiteIds);

    if (!caseIds.isEmpty()) {
      UUID environmentId =
          (req.environmentId() != null && !req.environmentId().isBlank())
              ? UUID.fromString(req.environmentId())
              : null;
      MatrixType matrixType = parseMatrix(req.matrixType());
      try {
        TestRun run =
            tcmRunService.createRun(
                projectId,
                req.name(),
                req.releaseVersion(),
                environmentId,
                caseIds,
                matrixType,
                req.triggeredBy(),
                releaseId,
                iterationPath,
                areaPath,
                teamId);
        return toDto(run);
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
      }
    }

    // Empty run (cases added later).
    TestRun run =
        new TestRun(
            projectId, req.name(), req.releaseVersion(), req.environment(), req.triggeredBy());
    run.setDimensions(releaseId, iterationPath, areaPath, teamId);
    run.setStartedAt(Instant.now());
    run = runRepo.save(run);
    return toDto(run);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /** Union of explicitly-selected case ids and all cases resolved from selected suites. */
  private List<UUID> effectiveCaseIds(
      UUID projectId, List<String> explicit, List<String> suiteIds) {
    java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
    if (explicit != null) for (String s : explicit) ids.add(UUID.fromString(s));
    if (suiteIds != null && !suiteIds.isEmpty()) {
      ids.addAll(
          suiteResolver.resolveMany(projectId, suiteIds.stream().map(UUID::fromString).toList()));
    }
    return new java.util.ArrayList<>(ids);
  }

  private UUID parseUuid(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return UUID.fromString(s.trim());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id: " + s);
    }
  }

  private MatrixType parseMatrix(String value) {
    if (value == null || value.isBlank()) return MatrixType.FULL;
    try {
      return MatrixType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid matrixType: " + value);
    }
  }

  public TestRunDto complete(UUID projectId, UUID runId) {
    TestRun run = loadAndVerify(projectId, runId);
    run.complete();
    run = runRepo.save(run);
    return toDto(run);
  }

  /**
   * Add existing APPROVED cases (and/or cases resolved from suites) to a live run. Cases already in
   * the run are skipped (idempotent). Only permitted while the run is IN_PROGRESS.
   */
  public TestRunDto addCases(UUID projectId, UUID runId, AddCasesRequest req) {
    TestRun run = loadAndVerify(projectId, runId);
    requireEditable(run);
    List<UUID> caseIds = effectiveCaseIds(projectId, req.testCaseIds(), req.suiteIds());
    if (!caseIds.isEmpty()) {
      try {
        tcmRunService.addCases(projectId, run, caseIds, parseMatrix(req.matrixType()));
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
      }
    }
    return toDto(run);
  }

  /**
   * Edit an in-progress run's scope (release / sprint / area / team) and environment. When a
   * release is chosen, any dimension left blank is inherited from the release's mapping (same as
   * create). Only permitted while the run is IN_PROGRESS.
   */
  public TestRunDto updateRun(UUID projectId, UUID runId, UpdateRunRequest req) {
    TestRun run = loadAndVerify(projectId, runId);
    requireEditable(run);

    if (!isBlank(req.name())) run.setName(req.name().trim());
    if (!isBlank(req.environment())) run.setEnvironment(req.environment().trim());
    run.setEnvironmentId(parseUuid(req.environmentId()));

    UUID releaseId = parseUuid(req.releaseId());
    UUID teamId = parseUuid(req.teamId());
    String iterationPath = req.iterationPath();
    String areaPath = req.areaPath();
    if (releaseId != null) {
      SotRelease rel =
          releaseRepo
              .findById(releaseId)
              .filter(r -> projectId.equals(r.getProjectId()))
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "Release not found in project: " + releaseId));
      if (isBlank(iterationPath)) iterationPath = rel.getMapIterationPath();
      if (isBlank(areaPath)) areaPath = rel.getMapAreaPath();
      if (teamId == null) teamId = rel.getMapTeamId();
    }
    run.setDimensions(releaseId, iterationPath, areaPath, teamId);
    run = runRepo.save(run);
    return toDto(run);
  }

  /** Reopen a completed run so it can be edited again (COMPLETED → IN_PROGRESS). */
  public TestRunDto reopen(UUID projectId, UUID runId) {
    TestRun run = loadAndVerify(projectId, runId);
    run.reopen();
    run = runRepo.save(run);
    return toDto(run);
  }

  public void delete(UUID projectId, UUID runId) {
    TestRun run = loadAndVerify(projectId, runId);
    List<TestCaseExecution> executions = execRepo.findByTestRunId(runId);
    execRepo.deleteAll(executions);
    runRepo.delete(run);
  }

  @Transactional(readOnly = true)
  public List<TestCaseExecutionDto> listExecutions(UUID projectId, UUID runId) {
    loadAndVerify(projectId, runId);
    List<TestCaseExecution> executions = execRepo.findByTestRunId(runId);
    List<UUID> tcIds = executions.stream().map(TestCaseExecution::getTestCaseId).toList();
    Map<UUID, String> titleMap =
        testCaseRepo.findAllById(tcIds).stream()
            .collect(Collectors.toMap(PlatformTestCase::getId, PlatformTestCase::getTitle));
    return executions.stream()
        .map(e -> TestCaseExecutionDto.from(e, titleMap.get(e.getTestCaseId())))
        .toList();
  }

  public TestCaseExecutionDto updateExecution(
      UUID projectId, UUID runId, UUID execId, UpdateExecutionRequest req) {
    requireEditable(loadAndVerify(projectId, runId));
    TestCaseExecution exec = loadExec(runId, execId);
    exec.record(req.status(), req.actualResult(), req.notes(), req.executedBy());
    exec = execRepo.save(exec);
    return toExecDto(exec);
  }

  /**
   * Link an <b>existing</b> ADO work item (validated via the read API) to a case execution. The
   * platform never writes to ADO — it only reads the work item to confirm it exists and cache its
   * title/state.
   */
  public TestCaseExecutionDto linkDefect(
      UUID projectId, UUID runId, UUID execId, LinkDefectRequest req) {
    requireEditable(loadAndVerify(projectId, runId));
    TestCaseExecution exec = loadExec(runId, execId);

    String workItemId = req == null || req.workItemId() == null ? null : req.workItemId().trim();
    if (workItemId == null || workItemId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workItemId is required");
    }

    AzureBoardsPollClient.Ado ado = adoClient.connect(projectId);
    if (ado == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No Azure DevOps credential configured for this project");
    }
    List<JsonNode> items;
    try {
      items = adoClient.getWorkItems(ado, List.of(workItemId)); // READ only
    } catch (RuntimeException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Azure DevOps lookup failed: " + e.getMessage());
    }
    if (items.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Work item " + workItemId + " not found in Azure DevOps");
    }
    JsonNode fields = items.get(0).path("fields");
    String title = textOrNull(fields.path("System.Title"));
    String state = textOrNull(fields.path("System.State"));
    String url = ado.root() + "/_workitems/edit/" + workItemId;

    exec.linkDefect(workItemId, url, title, state);
    exec = execRepo.save(exec);
    return toExecDto(exec);
  }

  /** Remove the linked ADO defect from a case execution. */
  public TestCaseExecutionDto unlinkDefect(UUID projectId, UUID runId, UUID execId) {
    requireEditable(loadAndVerify(projectId, runId));
    TestCaseExecution exec = loadExec(runId, execId);
    exec.clearDefect();
    exec = execRepo.save(exec);
    return toExecDto(exec);
  }

  // --- helpers ---

  private static String textOrNull(JsonNode n) {
    String v = n == null ? null : n.asText(null);
    return (v == null || v.isBlank()) ? null : v;
  }

  private TestCaseExecution loadExec(UUID runId, UUID execId) {
    TestCaseExecution exec =
        execRepo
            .findById(execId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Execution not found: " + execId));
    if (!exec.getTestRunId().equals(runId)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Execution not found for run: " + runId);
    }
    return exec;
  }

  private TestCaseExecutionDto toExecDto(TestCaseExecution exec) {
    String title =
        testCaseRepo.findById(exec.getTestCaseId()).map(PlatformTestCase::getTitle).orElse(null);
    return TestCaseExecutionDto.from(exec, title);
  }

  /** A run may only be mutated while IN_PROGRESS; a COMPLETED run must be reopened first. */
  private void requireEditable(TestRun run) {
    if (!run.isEditable()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run is " + run.getStatus() + "; reopen it before editing.");
    }
  }

  private TestRun loadAndVerify(UUID projectId, UUID runId) {
    TestRun run =
        runRepo
            .findById(runId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Test run not found: " + runId));
    if (!run.getProjectId().equals(projectId)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Test run not found for project: " + projectId);
    }
    return run;
  }

  private TestRunDto toDto(TestRun run) {
    List<TestCaseExecution> executions = execRepo.findByTestRunId(run.getId());
    long total = executions.size();
    long passed = executions.stream().filter(e -> "PASSED".equals(e.getStatus())).count();
    long failed = executions.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
    long blocked = executions.stream().filter(e -> "BLOCKED".equals(e.getStatus())).count();
    long skipped = executions.stream().filter(e -> "SKIPPED".equals(e.getStatus())).count();
    long pending = executions.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
    String releaseName =
        run.getReleaseId() == null
            ? null
            : releaseRepo.findById(run.getReleaseId()).map(SotRelease::getName).orElse(null);
    String teamName =
        run.getTeamId() == null
            ? null
            : teamRepo.findById(run.getTeamId()).map(AdoTeam::getName).orElse(null);
    return TestRunDto.from(
        run, total, passed, failed, blocked, skipped, pending, releaseName, teamName);
  }
}
