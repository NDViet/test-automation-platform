package com.platform.ingestion.quality;

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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Computes the four release-scoped quality signals the gate evaluates, from data the platform
 * already holds: pass-rate over the release's TCM-run executions, automation coverage over the
 * release's mapped scope, and project-wide counts of open failure clusters and critical-flaky
 * tests.
 *
 * <p>Pass-rate is release-scoped via {@code test_runs.release_id}; coverage reuses the release's
 * composite ADO mapping (area/team/iteration). A release with no executed runs yields passRate 0
 * and {@code suitesExecuted=false} so a gate can refuse to ship a release with no evidence.
 */
@Service
public class ReleaseGateMetricsService {

  private final SotReleaseRepository releaseRepo;
  private final TestRunRepository testRunRepo;
  private final TestCaseExecutionRepository execRepo;
  private final CoverageService coverageService;
  private final FailureClusterRepository clusterRepo;
  private final FlakinessScoreRepository flakinessRepo;

  public ReleaseGateMetricsService(
      SotReleaseRepository releaseRepo,
      TestRunRepository testRunRepo,
      TestCaseExecutionRepository execRepo,
      CoverageService coverageService,
      FailureClusterRepository clusterRepo,
      FlakinessScoreRepository flakinessRepo) {
    this.releaseRepo = releaseRepo;
    this.testRunRepo = testRunRepo;
    this.execRepo = execRepo;
    this.coverageService = coverageService;
    this.clusterRepo = clusterRepo;
    this.flakinessRepo = flakinessRepo;
  }

  /** The four gate inputs for one release. */
  public record ReleaseGateMetrics(
      double passRate,
      double coveragePct,
      int openBlockers,
      int criticalFlaky,
      boolean suitesExecuted,
      int totalExecutions) {}

  @Transactional(readOnly = true)
  public ReleaseGateMetrics forRelease(UUID projectId, UUID releaseId) {
    SotRelease release =
        releaseRepo
            .findById(releaseId)
            .filter(r -> projectId.equals(r.getProjectId()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Release not found: " + releaseId));

    int total = 0;
    int passed = 0;
    for (TestRun run : testRunRepo.findByProjectIdAndReleaseId(projectId, releaseId)) {
      for (TestCaseExecution exec : execRepo.findByTestRunId(run.getId())) {
        String s = exec.getStatus();
        if ("PENDING".equals(s)) {
          continue; // not yet executed — doesn't count for or against pass-rate
        }
        total++;
        if ("PASSED".equals(s)) {
          passed++;
        }
      }
    }
    double passRate = total == 0 ? 0.0 : (double) passed / total;

    CoverageDto coverage =
        coverageService.coverage(
            projectId,
            release.getMapAreaPath(),
            release.getMapTeamId() != null ? release.getMapTeamId().toString() : null,
            release.getMapIterationPath());

    int openBlockers = (int) clusterRepo.countByProjectIdAndStatus(projectId, "OPEN");
    int criticalFlaky =
        (int)
            flakinessRepo.countByProjectIdAndClassification(
                projectId, FlakinessScore.Classification.CRITICAL_FLAKY);

    return new ReleaseGateMetrics(
        passRate, coverage.automationCoveragePct(), openBlockers, criticalFlaky, total > 0, total);
  }
}
