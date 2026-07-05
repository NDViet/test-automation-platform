package com.platform.ingestion.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.FailureCluster;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.quality.FailureClusterService;
import com.platform.core.repository.FailureClusterRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.service.MatrixType;
import com.platform.core.service.TcmRunService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FailureClusterRetestServiceTest {

  @Mock FailureClusterService clusters;
  @Mock FailureClusterRepository clusterRepo;
  @Mock TcmRunService tcmRunService;
  @Mock TestCaseExecutionRepository execRepo;

  FailureClusterRetestService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID cid = UUID.randomUUID();
  private final UUID runId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new FailureClusterRetestService(clusters, clusterRepo, tcmRunService, execRepo);
  }

  /** Manual cluster: testRef is a platform test-case id. */
  private FailureCluster manualCluster() {
    return new FailureCluster(pid, "MANUAL", "sig", caseId.toString(), "Login broke", "boom");
  }

  private TestCaseExecution exec(String status) {
    TestCaseExecution e = new TestCaseExecution(runId, caseId);
    e.record(status, null, null, null);
    return e;
  }

  @Test
  void retestCreatesTargetedRunWithTheClustersCaseAndLinksIt() {
    when(clusters.get(pid, cid)).thenReturn(manualCluster());
    TestRun run = mock(TestRun.class);
    when(run.getId()).thenReturn(runId);
    when(tcmRunService.createRun(
            eq(pid), any(), any(), any(), eq(List.of(caseId)), eq(MatrixType.FULL), any()))
        .thenReturn(run);

    service.retest(pid, cid, "alice");

    ArgumentCaptor<FailureCluster> saved = ArgumentCaptor.forClass(FailureCluster.class);
    verify(clusterRepo).save(saved.capture());
    assertThat(saved.getValue().getRetestRunId()).isEqualTo(runId);
  }

  @Test
  void retestOfAutomatedClusterIsRejected() {
    FailureCluster automated =
        new FailureCluster(pid, "AUTOMATED", "sig", "acme.LoginTest#login", "NPE", "boom");
    when(clusters.get(pid, cid)).thenReturn(automated);

    assertThatThrownBy(() -> service.retest(pid, cid, "alice"))
        .isInstanceOf(ResponseStatusException.class);
    verify(clusterRepo, never()).save(any());
  }

  @Test
  void rollUpResolvesWhenAllExecutionsPass() {
    FailureCluster c = manualCluster();
    c.setStatus("OPEN");
    when(clusterRepo.findByRetestRunId(runId)).thenReturn(Optional.of(c));
    when(execRepo.findByTestRunId(runId)).thenReturn(List.of(exec("PASSED"), exec("PASSED")));

    service.rollUp(runId);

    assertThat(c.getStatus()).isEqualTo("RESOLVED");
    assertThat(c.getRetestRunId()).isNull();
    verify(clusterRepo).save(c);
  }

  @Test
  void rollUpReopensWhenAnyExecutionFails() {
    FailureCluster c = manualCluster();
    c.setStatus("RESOLVED");
    when(clusterRepo.findByRetestRunId(runId)).thenReturn(Optional.of(c));
    when(execRepo.findByTestRunId(runId)).thenReturn(List.of(exec("PASSED"), exec("FAILED")));

    service.rollUp(runId);

    assertThat(c.getStatus()).isEqualTo("OPEN");
    verify(clusterRepo).save(c);
  }

  @Test
  void rollUpWaitsWhileExecutionsStillPending() {
    FailureCluster c = manualCluster();
    when(clusterRepo.findByRetestRunId(runId)).thenReturn(Optional.of(c));
    when(execRepo.findByTestRunId(runId)).thenReturn(List.of(exec("PASSED"), exec("PENDING")));

    service.rollUp(runId);

    verify(clusterRepo, never()).save(any());
  }

  @Test
  void rollUpIgnoresRunsThatAreNotClusterRetests() {
    when(clusterRepo.findByRetestRunId(runId)).thenReturn(Optional.empty());

    service.rollUp(runId);

    verify(execRepo, never()).findByTestRunId(any());
    verify(clusterRepo, never()).save(any());
  }
}
