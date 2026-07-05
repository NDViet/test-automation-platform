package com.platform.ingestion.quality;

import com.platform.core.domain.FailureCluster;
import com.platform.core.quality.FailureClusterService;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import com.platform.integration.port.IssueReference;
import com.platform.integration.port.IssueRequest;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.TrackerFactoryRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a NEW tracker defect for a failure cluster and links it back. This is the only cluster
 * operation that reaches outside the platform, so it lives in ingestion (which has the web/security
 * layer) rather than core: it resolves the project's first configured issue tracker, creates a work
 * item through the existing {@link IssueTrackerPort}, then records the returned id/url on the
 * cluster via {@link FailureClusterService#linkDefect}.
 *
 * <p>We deliberately do NOT hold a defect entity in the platform — ADO/Jira/GitHub remains the
 * single source of truth; the cluster just carries a link.
 */
@Service
public class FailureClusterDefectService {

  private final FailureClusterService clusters;
  private final CredentialResolver credentialResolver;
  private final TrackerFactoryRegistry trackerRegistry;

  public FailureClusterDefectService(
      FailureClusterService clusters,
      CredentialResolver credentialResolver,
      TrackerFactoryRegistry trackerRegistry) {
    this.clusters = clusters;
    this.credentialResolver = credentialResolver;
    this.trackerRegistry = trackerRegistry;
  }

  /**
   * Resolves the project's first configured tracker, creates a defect for the cluster, and links
   * it.
   *
   * @throws IllegalStateException if the project has no usable issue-tracker credential
   */
  @Transactional
  public FailureCluster createDefect(
      UUID projectId, UUID clusterId, String issueType, String priority, String description) {
    FailureCluster cluster = clusters.get(projectId, clusterId);

    for (String type : trackerRegistry.lifecycleTypes()) {
      Optional<ResolvedCredential> credOpt = credentialResolver.resolve(projectId, type);
      if (credOpt.isEmpty() || !credOpt.get().hasSecret()) {
        continue;
      }
      Optional<IssueTrackerPort> trackerOpt = trackerRegistry.build(type, credOpt.get());
      if (trackerOpt.isEmpty()) {
        continue;
      }
      ResolvedCredential cred = credOpt.get();
      IssueTrackerPort tracker = trackerOpt.get();

      String title =
          (cluster.getTitle() != null && !cluster.getTitle().isBlank())
              ? cluster.getTitle()
              : "Failure cluster " + clusterId;
      String type2 = (issueType != null && !issueType.isBlank()) ? issueType : "Bug";
      String prio = (priority != null && !priority.isBlank()) ? priority : "Medium";
      String desc =
          (description != null && !description.isBlank()) ? description : describe(cluster);

      IssueRequest req =
          new IssueRequest(
              title,
              desc,
              type2,
              prio,
              cred.param("project_key"),
              List.of("platform-quality", "failure-cluster"),
              cluster.getTestRef(),
              null);

      IssueReference ref = tracker.createIssue(req);
      return clusters.linkDefect(projectId, clusterId, ref.key(), ref.url(), title, ref.status());
    }

    throw new IllegalStateException(
        "No issue tracker is configured for project "
            + projectId
            + "; link an existing defect instead.");
  }

  private static String describe(FailureCluster cluster) {
    StringBuilder sb = new StringBuilder();
    sb.append("Auto-filed from a failure cluster on the test platform.\n\n");
    if (cluster.getTestRef() != null) {
      sb.append("Test: ").append(cluster.getTestRef()).append('\n');
    }
    sb.append("Occurrences: ").append(cluster.getOccurrences()).append('\n');
    if (cluster.getSampleMessage() != null) {
      sb.append("\nSample failure:\n").append(cluster.getSampleMessage());
    }
    return sb.toString();
  }
}
