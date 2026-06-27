package com.platform.ingestion.management.tcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.core.domain.SotRelease;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.TestRunRepository;
import com.platform.core.repository.TestSuiteRepository;
import com.platform.core.service.TcmRunService;
import com.platform.core.service.ado.AzureBoardsPollClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TestRunServiceTest {

  private TestRunRepository runRepo;
  private TestCaseExecutionRepository execRepo;
  private PlatformTestCaseRepository testCaseRepo;
  private AzureBoardsPollClient adoClient;
  private TcmRunService tcmRunService;
  private SotReleaseRepository releaseRepo;
  private TestRunService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID runId = UUID.randomUUID();
  private final UUID execId = UUID.randomUUID();
  private final ObjectMapper om = new ObjectMapper();

  @BeforeEach
  void setUp() {
    runRepo = mock(TestRunRepository.class);
    execRepo = mock(TestCaseExecutionRepository.class);
    testCaseRepo = mock(PlatformTestCaseRepository.class);
    adoClient = mock(AzureBoardsPollClient.class);
    tcmRunService = mock(TcmRunService.class);
    releaseRepo = mock(SotReleaseRepository.class);
    service =
        new TestRunService(
            runRepo,
            execRepo,
            testCaseRepo,
            tcmRunService,
            releaseRepo,
            mock(AdoTeamRepository.class),
            mock(SuiteResolverService.class),
            mock(TestSuiteRepository.class),
            adoClient);
    // toDto() reads executions for counters
    lenient().when(execRepo.findByTestRunId(any())).thenReturn(List.of());
    lenient().when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(execRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(testCaseRepo.findById(any())).thenReturn(Optional.empty());
  }

  private TestRun run(String status) {
    TestRun r = new TestRun(projectId, "Run", "1.0", "STAGING", "alice");
    if ("COMPLETED".equals(status)) r.complete();
    when(runRepo.findById(runId)).thenReturn(Optional.of(r));
    return r;
  }

  @Test
  void reopenFlipsCompletedRunToInProgress() {
    TestRun r = run("COMPLETED");
    assertThat(r.getStatus()).isEqualTo("COMPLETED");

    TestRunDto dto = service.reopen(projectId, runId);

    assertThat(r.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(r.getCompletedAt()).isNull();
    assertThat(dto.status()).isEqualTo("IN_PROGRESS");
    verify(runRepo).save(r);
  }

  @Test
  void updateExecutionRejectedWhenRunCompleted() {
    run("COMPLETED");

    assertThatThrownBy(
            () ->
                service.updateExecution(
                    projectId,
                    runId,
                    execId,
                    new UpdateExecutionRequest("PASSED", null, null, "bob")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

    // guard must fire before touching the execution store
    verify(execRepo, never()).findById(any());
    verify(execRepo, never()).save(any());
  }

  @Test
  void addCasesRejectedWhenRunCompleted() {
    run("COMPLETED");

    assertThatThrownBy(
            () ->
                service.addCases(
                    projectId,
                    runId,
                    new AddCasesRequest(List.of(UUID.randomUUID().toString()), null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
  }

  @Test
  void updateRunSetsScopeAndEnvironmentWhenInProgress() {
    TestRun r = run("IN_PROGRESS");
    UUID teamId = UUID.randomUUID();

    service.updateRun(
        projectId,
        runId,
        new UpdateRunRequest(
            null,
            "PROD",
            null,
            null,
            "Product House\\Sprint 6",
            "Product House\\Pay",
            teamId.toString()));

    assertThat(r.getEnvironment()).isEqualTo("PROD");
    assertThat(r.getIterationPath()).isEqualTo("Product House\\Sprint 6");
    assertThat(r.getAreaPath()).isEqualTo("Product House\\Pay");
    assertThat(r.getTeamId()).isEqualTo(teamId);
    verify(runRepo).save(r);
  }

  @Test
  void updateRunRejectedWhenRunCompleted() {
    run("COMPLETED");

    assertThatThrownBy(
            () ->
                service.updateRun(
                    projectId,
                    runId,
                    new UpdateRunRequest(null, "PROD", null, null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    verify(runRepo, never()).save(any());
  }

  // ── T3: link existing ADO defect (read-only) ─────────────────────────────

  private TestCaseExecution execInRun() {
    TestCaseExecution exec = new TestCaseExecution(runId, UUID.randomUUID());
    when(execRepo.findById(execId)).thenReturn(Optional.of(exec));
    return exec;
  }

  @Test
  void linkDefectValidatesViaReadApiAndStoresIdUrlTitleState() {
    run("IN_PROGRESS");
    TestCaseExecution exec = execInRun();
    AzureBoardsPollClient.Ado ado =
        new AzureBoardsPollClient.Ado("https://dev.azure.com/acme", "Basic x");
    when(adoClient.connect(projectId)).thenReturn(ado);
    ObjectNode wi = om.createObjectNode();
    ObjectNode fields = wi.putObject("fields");
    fields.put("System.Title", "Login fails");
    fields.put("System.State", "Active");
    when(adoClient.getWorkItems(ado, List.of("24336"))).thenReturn(List.of(wi));

    TestCaseExecutionDto dto =
        service.linkDefect(projectId, runId, execId, new LinkDefectRequest("24336"));

    assertThat(exec.getDefectExternalId()).isEqualTo("24336");
    assertThat(exec.getDefectUrl()).isEqualTo("https://dev.azure.com/acme/_workitems/edit/24336");
    assertThat(exec.getDefectTitle()).isEqualTo("Login fails");
    assertThat(exec.getDefectState()).isEqualTo("Active");
    assertThat(dto.defectId()).isEqualTo("24336");

    // Read-only guarantee: the only ADO calls are connect + getWorkItems — no write methods exist.
    verify(adoClient).connect(projectId);
    verify(adoClient).getWorkItems(ado, List.of("24336"));
    verifyNoMoreInteractions(adoClient);
  }

  @Test
  void linkDefectRejectsUnknownWorkItem() {
    run("IN_PROGRESS");
    execInRun();
    AzureBoardsPollClient.Ado ado =
        new AzureBoardsPollClient.Ado("https://dev.azure.com/acme", "Basic x");
    when(adoClient.connect(projectId)).thenReturn(ado);
    when(adoClient.getWorkItems(ado, List.of("999"))).thenReturn(List.of()); // not found

    assertThatThrownBy(
            () -> service.linkDefect(projectId, runId, execId, new LinkDefectRequest("999")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

    verify(execRepo, never()).save(any());
  }

  @Test
  void linkDefectRejectedWhenRunCompleted() {
    run("COMPLETED");

    assertThatThrownBy(
            () -> service.linkDefect(projectId, runId, execId, new LinkDefectRequest("24336")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

    verifyNoMoreInteractions(adoClient); // never even reached ADO
  }

  // ── T5/F1: create inherits scope from the chosen release ─────────────────

  @Test
  void createInheritsBlankScopeFromRelease() {
    UUID releaseId = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    SotRelease rel = mock(SotRelease.class);
    when(rel.getProjectId()).thenReturn(projectId);
    when(rel.getMapIterationPath()).thenReturn("Product House\\Sprint 5");
    when(rel.getMapAreaPath()).thenReturn("Product House\\Checkout");
    when(rel.getMapTeamId()).thenReturn(teamId);
    when(releaseRepo.findById(releaseId)).thenReturn(Optional.of(rel));
    when(tcmRunService.createRun(
            any(), any(), any(), any(), anyList(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new TestRun(projectId, "Run", null, "STAGING", "alice"));

    CreateTestRunRequest req =
        new CreateTestRunRequest(
            "Run",
            null,
            null,
            "alice",
            List.of(caseId.toString()),
            null,
            null,
            null,
            releaseId.toString(),
            null,
            null,
            null); // blank iteration/area/team

    service.create(projectId, req);

    // the blank dimensions are filled from the release mapping
    verify(tcmRunService)
        .createRun(
            eq(projectId),
            eq("Run"),
            any(),
            any(),
            anyList(),
            any(),
            eq("alice"),
            eq(releaseId),
            eq("Product House\\Sprint 5"),
            eq("Product House\\Checkout"),
            eq(teamId));
  }

  @Test
  void unlinkDefectClearsTheLink() {
    run("IN_PROGRESS");
    TestCaseExecution exec = execInRun();
    exec.linkDefect("24336", "url", "t", "Active");

    service.unlinkDefect(projectId, runId, execId);

    assertThat(exec.getDefectExternalId()).isNull();
    assertThat(exec.getDefectUrl()).isNull();
    verifyNoMoreInteractions(adoClient); // unlink never calls ADO
  }
}
