package com.platform.core.quality;

import com.platform.core.domain.FailureCluster;
import com.platform.core.domain.FailureOccurrence;
import com.platform.core.repository.FailureClusterRepository;
import com.platform.core.repository.FailureOccurrenceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a failed execution into its failure cluster (creating the cluster on first sight). Lives
 * in platform-core so both the manual-execution path (ingestion) and the automated-result path
 * (analytics/agent) can call it without cross-module coupling.
 *
 * <p>Rules: dedup occurrences per execution; a RESOLVED cluster reopens when a genuinely new
 * occurrence lands; a KNOWN_ISSUE cluster keeps counting but is never demoted (its suppression is
 * the user's decision, not ours).
 */
@Service
public class FailureClusterService {

  private final FailureClusterRepository clusterRepo;
  private final FailureOccurrenceRepository occurrenceRepo;

  public FailureClusterService(
      FailureClusterRepository clusterRepo, FailureOccurrenceRepository occurrenceRepo) {
    this.clusterRepo = clusterRepo;
    this.occurrenceRepo = occurrenceRepo;
  }

  @Transactional
  public FailureCluster record(
      UUID projectId,
      String source,
      String signatureHash,
      String testRef,
      String message,
      UUID executionId,
      UUID runId) {

    FailureCluster cluster =
        clusterRepo
            .findByProjectIdAndSignatureHash(projectId, signatureHash)
            .orElseGet(
                () ->
                    clusterRepo.save(
                        new FailureCluster(
                            projectId,
                            source,
                            signatureHash,
                            testRef,
                            title(message),
                            sample(message))));

    boolean newOccurrence =
        executionId == null
            || !occurrenceRepo.existsByClusterIdAndExecutionId(cluster.getId(), executionId);

    if (newOccurrence) {
      if (executionId != null) {
        occurrenceRepo.save(new FailureOccurrence(cluster.getId(), executionId, runId));
      }
      cluster.setOccurrences(cluster.getOccurrences() + 1);
      cluster.setLastSeenAt(Instant.now());
      // A recurrence reopens a resolved cluster; a known issue stays suppressed.
      if ("RESOLVED".equals(cluster.getStatus())) {
        cluster.setStatus("OPEN");
      }
    }

    return clusterRepo.save(cluster);
  }

  /**
   * Convenience for automated results: only clusters FAILED/BROKEN, derives the signature from the
   * failure message + stack trace, and uses the CI test id as the ref. Returns null for
   * non-failures so callers can hook it unconditionally on every persisted result.
   */
  @Transactional
  public FailureCluster recordAutomated(
      UUID projectId,
      String testId,
      String status,
      String failureMessage,
      String stackTrace,
      UUID resultId,
      UUID runId) {
    if (!("FAILED".equals(status) || "BROKEN".equals(status))) {
      return null;
    }
    String message =
        (failureMessage != null && !failureMessage.isBlank()) ? failureMessage : stackTrace;
    return record(
        projectId,
        "AUTOMATED",
        FailureSignature.compute(failureMessage, stackTrace),
        testId,
        message,
        resultId,
        runId);
  }

  /** Statuses a user may set directly. AUTOMATED reopen/close transitions are handled elsewhere. */
  private static final Set<String> SETTABLE_STATUSES = Set.of("OPEN", "KNOWN_ISSUE", "RESOLVED");

  /** Lists a project's clusters newest-activity-first, optionally filtered by status. */
  @Transactional(readOnly = true)
  public List<FailureCluster> list(UUID projectId, String status) {
    return (status == null || status.isBlank())
        ? clusterRepo.findByProjectIdOrderByLastSeenAtDesc(projectId)
        : clusterRepo.findByProjectIdAndStatusOrderByLastSeenAtDesc(projectId, status);
  }

  /** Loads a single cluster scoped to its project, or throws if it doesn't belong there. */
  @Transactional(readOnly = true)
  public FailureCluster get(UUID projectId, UUID id) {
    return clusterRepo
        .findByIdAndProjectId(id, projectId)
        .orElseThrow(() -> new IllegalArgumentException("Failure cluster not found: " + id));
  }

  /** Sets the cluster status (OPEN / KNOWN_ISSUE / RESOLVED). Suppression is the user's call. */
  @Transactional
  public FailureCluster updateStatus(UUID projectId, UUID id, String status) {
    if (status == null || !SETTABLE_STATUSES.contains(status)) {
      throw new IllegalArgumentException("Invalid status: " + status);
    }
    FailureCluster cluster = get(projectId, id);
    cluster.setStatus(status);
    return clusterRepo.save(cluster);
  }

  /**
   * Links the cluster to an existing tracker defect (does not create one — see the ingestion side).
   */
  @Transactional
  public FailureCluster linkDefect(
      UUID projectId, UUID id, String externalId, String url, String title, String state) {
    if (externalId == null || externalId.isBlank()) {
      throw new IllegalArgumentException("Defect external id is required");
    }
    FailureCluster cluster = get(projectId, id);
    cluster.setDefect(externalId, url, title, state);
    return clusterRepo.save(cluster);
  }

  private static String title(String message) {
    if (message == null || message.isBlank()) return "Unknown failure";
    String first = message.strip().split("\\R", 2)[0].strip();
    return first.length() > 200 ? first.substring(0, 200) : first;
  }

  private static String sample(String message) {
    if (message == null) return null;
    return message.length() > 2000 ? message.substring(0, 2000) : message;
  }
}
