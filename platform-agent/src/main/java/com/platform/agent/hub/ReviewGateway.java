package com.platform.agent.hub;

import com.platform.common.agent.*;
import java.util.UUID;

/**
 * Hub component that routes NodeResults requiring human review to the appropriate channel.
 * Publishes to agent.approval.requests Kafka topic; consumes decisions from
 * agent.approval.decisions.
 */
public interface ReviewGateway {

  /**
   * Route a NodeResult that has status AWAITING_REVIEW to the configured review channel. Publishes
   * an approval request and returns the review request ID.
   */
  UUID requestReview(NodeResult result, ContextBundle bundle);

  /**
   * Apply a human decision to a pending review. If APPROVED, publishes the artifact manifest and
   * resumes the workflow. If REJECTED, marks the workflow step as rejected (node does not retry
   * automatically). If EDIT, stores the edited payload and resumes with the edited content.
   */
  void applyDecision(UUID reviewRequestId, ReviewDecision decision, String editedPayload);
}
