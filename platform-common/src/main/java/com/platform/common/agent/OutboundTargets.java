package com.platform.common.agent;

import com.platform.common.integration.IntegrationType;

/**
 * Where nodes should push their artifacts. All fields are optional — null means "no target
 * configured for this artifact type".
 */
public record OutboundTargets(
    RepoTarget testCaseTarget, // where to create/update manual test cases
    RepoTarget codeTarget, // where to commit generated/healed test code
    IssueTarget issueTarget, // where to create/update tickets
    ReviewTarget reviewTarget // where to post review requests
    ) {
  /** Target for test management system (TestRail, Xray, QASE, etc.) */
  public record RepoTarget(
      IntegrationType system,
      String destinationRef, // project key, suite ID, folder path
      String baseUrl) {}

  /** Target for issue tracker (JIRA, Linear, GitHub Issues, etc.) */
  public record IssueTarget(IntegrationType system, String projectKey, String baseUrl) {}

  /** Target for human-in-the-loop review */
  public record ReviewTarget(
      ReviewChannel channel, String destination // Slack channel, GitHub repo, portal project ID
      ) {}

  public static OutboundTargets reviewOnly(ReviewChannel channel, String destination) {
    return new OutboundTargets(null, null, null, new ReviewTarget(channel, destination));
  }
}
