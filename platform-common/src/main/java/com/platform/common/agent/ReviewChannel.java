package com.platform.common.agent;

/** HITL channel used to route a review request for human approval. */
public enum ReviewChannel {
  SLACK, // Interactive Block Kit message — approve / reject / edit
  PORTAL, // /approvals queue in the platform portal
  GITHUB_PR, // Draft PR — human reviews and merges
  AUTO // Skip human review when qualityScore exceeds the configured threshold
}
