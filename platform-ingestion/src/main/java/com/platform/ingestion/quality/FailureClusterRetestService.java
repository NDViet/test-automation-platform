package com.platform.ingestion.quality;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Retest loop for a failure cluster: spin up a targeted TCM run of the cluster's case, then, once
 * that run's executions finish, roll the outcome back onto the cluster — all pass → RESOLVED, any
 * fail → OPEN.
 *
 * <p>Only manual clusters (whose {@code testRef} is a platform test-case id) can be retested here;
 * automated clusters need a CI dispatch, which arrives in a later epic. All dependencies are
 * platform-core services, so this stays free of any cycle with {@code TestRunService} (which calls
 * {@link #rollUp} on every execution update).
 */
@Service
public class FailureClusterRetestService {

  private final FailureClusterService clusters;
  private final FailureClusterRepository clusterRepo;
  private final TcmRunService tcmRunService;
  private final TestCaseExecutionRepository execRepo;

  public FailureClusterRetestService(
      FailureClusterService clusters,
      FailureClusterRepository clusterRepo,
      TcmRunService tcmRunService,
      TestCaseExecutionRepository execRepo) {
    this.clusters = clusters;
    this.clusterRepo = clusterRepo;
    this.tcmRunService = tcmRunService;
    this.execRepo = execRepo;
  }

  /** Creates a targeted retest run of the cluster's case and links the run to the cluster. */
  @Transactional
  public TestRun retest(UUID projectId, UUID clusterId, String triggeredBy) {
    FailureCluster cluster = clusters.get(projectId, clusterId);
    UUID caseId = resolveCaseId(cluster.getTestRef());

    TestRun run;
    try {
      run =
          tcmRunService.createRun(
              projectId,
              "Retest: " + (cluster.getTitle() != null ? cluster.getTitle() : clusterId),
              null,
              null,
              List.of(caseId),
              MatrixType.FULL,
              triggeredBy);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    cluster.setRetestRunId(run.getId());
    clusterRepo.save(cluster);
    return run;
  }

  /**
   * If {@code runId} is a cluster's retest run and every execution has finished, resolve the
   * cluster when they all pass or reopen it otherwise. No-op when the run isn't a retest or is
   * still running.
   */
  @Transactional
  public void rollUp(UUID runId) {
    Optional<FailureCluster> opt = clusterRepo.findByRetestRunId(runId);
    if (opt.isEmpty()) {
      return;
    }
    List<TestCaseExecution> execs = execRepo.findByTestRunId(runId);
    if (execs.isEmpty() || execs.stream().anyMatch(e -> "PENDING".equals(e.getStatus()))) {
      return; // wait until the retest is fully executed
    }

    boolean allPassed = execs.stream().allMatch(e -> "PASSED".equals(e.getStatus()));
    FailureCluster cluster = opt.get();
    cluster.setStatus(allPassed ? "RESOLVED" : "OPEN");
    cluster.setRetestRunId(null); // retest evaluated — unlink
    clusterRepo.save(cluster);
  }

  private static UUID resolveCaseId(String testRef) {
    if (testRef != null) {
      try {
        return UUID.fromString(testRef.trim());
      } catch (IllegalArgumentException ignored) {
        // fall through to the automated-cluster error
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "This cluster comes from automated tests; targeted retest via CI arrives in a later epic. "
            + "Track it through the linked defect instead.");
  }
}
