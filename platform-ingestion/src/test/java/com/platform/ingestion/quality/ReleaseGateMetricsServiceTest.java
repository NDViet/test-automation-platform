package com.platform.ingestion.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.core.domain.FlakinessScore;
import com.platform.core.domain.SotRelease;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.FailureClusterRepository;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.TestRunRepository;
import com.platform.ingestion.management.tcm.CoverageDto;
import com.platform.ingestion.management.tcm.CoverageService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseGateMetricsServiceTest {

  @Mock SotReleaseRepository releaseRepo;
  @Mock TestRunRepository testRunRepo;
  @Mock TestCaseExecutionRepository execRepo;
  @Mock CoverageService coverageService;
  @Mock FailureClusterRepository clusterRepo;
  @Mock FlakinessScoreRepository flakinessRepo;

  ReleaseGateMetricsService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID releaseId = UUID.randomUUID();
  private final UUID runId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new ReleaseGateMetricsService(
            releaseRepo, testRunRepo, execRepo, coverageService, clusterRepo, flakinessRepo);
    when(releaseRepo.findById(releaseId)).thenReturn(Optional.of(release()));
    lenient()
        .when(coverageService.coverage(eq(pid), any(), any(), any()))
        .thenReturn(new CoverageDto(10, 8, 1, 1, 80.0, List.of(), List.of(), List.of()));
    lenient().when(clusterRepo.countByProjectIdAndStatus(pid, "OPEN")).thenReturn(2L);
    lenient()
        .when(
            flakinessRepo.countByProjectIdAndClassification(
                pid, FlakinessScore.Classification.CRITICAL_FLAKY))
        .thenReturn(1L);
  }

  private SotRelease release() {
    return new SotRelease(pid, "v1.0", "VERSION", null);
  }

  private TestCaseExecution exec(String status) {
    TestCaseExecution e = new TestCaseExecution(runId, UUID.randomUUID());
    e.record(status, null, null, null);
    return e;
  }

  @Test
  void aggregatesPassRateCoverageBlockersAndFlaky() {
    TestRun run = mock(TestRun.class);
    when(run.getId()).thenReturn(runId);
    when(testRunRepo.findByProjectIdAndReleaseId(pid, releaseId)).thenReturn(List.of(run));
    when(execRepo.findByTestRunId(runId))
        .thenReturn(List.of(exec("PASSED"), exec("PASSED"), exec("FAILED"), exec("PENDING")));

    var m = service.forRelease(pid, releaseId);

    assertThat(m.passRate()).isEqualTo(2.0 / 3); // PENDING excluded
    assertThat(m.totalExecutions()).isEqualTo(3);
    assertThat(m.coveragePct()).isEqualTo(80.0);
    assertThat(m.openBlockers()).isEqualTo(2);
    assertThat(m.criticalFlaky()).isEqualTo(1);
    assertThat(m.suitesExecuted()).isTrue();
  }

  @Test
  void noRunsMeansZeroPassRateAndNotExecuted() {
    when(testRunRepo.findByProjectIdAndReleaseId(pid, releaseId)).thenReturn(List.of());

    var m = service.forRelease(pid, releaseId);

    assertThat(m.passRate()).isEqualTo(0.0);
    assertThat(m.suitesExecuted()).isFalse();
    assertThat(m.openBlockers()).isEqualTo(2);
  }
}
