package com.platform.core.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestCaseProperty;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TcmRunServiceTest {

  private TestRunRepository runRepo;
  private TestCaseExecutionRepository executionRepo;
  private PlatformTestCaseRepository testCaseRepo;
  private TestCasePropertyRepository casePropertyRepo;
  private TestExecutionPropertyRepository executionPropertyRepo;
  private EnvironmentRepository environmentRepo;
  private TcmRunService service;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    runRepo = mock(TestRunRepository.class);
    executionRepo = mock(TestCaseExecutionRepository.class);
    testCaseRepo = mock(PlatformTestCaseRepository.class);
    casePropertyRepo = mock(TestCasePropertyRepository.class);
    executionPropertyRepo = mock(TestExecutionPropertyRepository.class);
    environmentRepo = mock(EnvironmentRepository.class);
    service =
        new TcmRunService(
            runRepo,
            executionRepo,
            testCaseRepo,
            casePropertyRepo,
            executionPropertyRepo,
            environmentRepo,
            new PropertyMatrixService());

    when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(executionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  private PlatformTestCase tc(UUID id, String status) {
    PlatformTestCase t = mock(PlatformTestCase.class);
    when(t.getId()).thenReturn(id);
    when(t.getProjectId()).thenReturn(projectId);
    when(t.getStatus()).thenReturn(status);
    return t;
  }

  @Test
  void confirmedGate_rejectsNonApprovedCase() {
    UUID caseId = UUID.randomUUID();
    PlatformTestCase draft = tc(caseId, "DRAFT");
    when(testCaseRepo.findById(caseId)).thenReturn(java.util.Optional.of(draft));

    assertThatThrownBy(
            () ->
                service.createRun(
                    projectId, "Run", null, null, List.of(caseId), MatrixType.FULL, "tester"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be APPROVED");

    verify(executionRepo, never()).save(any());
  }

  @Test
  void approvedCaseWithoutProperties_createsSingleExecution() {
    UUID caseId = UUID.randomUUID();
    PlatformTestCase approved = tc(caseId, "APPROVED");
    when(testCaseRepo.findById(caseId)).thenReturn(java.util.Optional.of(approved));
    when(casePropertyRepo.findByTestCaseId(caseId)).thenReturn(List.of());

    service.createRun(projectId, "Run", null, null, List.of(caseId), MatrixType.FULL, "tester");

    verify(executionRepo, times(1)).save(any(TestCaseExecution.class));
    verify(executionPropertyRepo, never()).save(any());
  }

  @Test
  void parametrizedCase_full_createsExecutionPerCombination() {
    UUID caseId = UUID.randomUUID();
    PlatformTestCase approved = tc(caseId, "APPROVED");
    when(testCaseRepo.findById(caseId)).thenReturn(java.util.Optional.of(approved));
    when(casePropertyRepo.findByTestCaseId(caseId))
        .thenReturn(
            List.of(
                new TestCaseProperty(caseId, "browser", "Chrome"),
                new TestCaseProperty(caseId, "browser", "Firefox"),
                new TestCaseProperty(caseId, "os", "Linux"),
                new TestCaseProperty(caseId, "os", "Windows")));

    TestRun run =
        service.createRun(
            projectId, "Matrix Run", "v1", null, List.of(caseId), MatrixType.FULL, "tester");

    assertThat(run).isNotNull();
    verify(executionRepo, times(4)).save(any(TestCaseExecution.class)); // 2x2 full
    verify(executionPropertyRepo, times(8)).save(any()); // 4 combos x 2 props
  }

  @Test
  void emptyCaseList_isRejected() {
    assertThatThrownBy(
            () ->
                service.createRun(
                    projectId, "Run", null, null, List.of(), MatrixType.FULL, "tester"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
