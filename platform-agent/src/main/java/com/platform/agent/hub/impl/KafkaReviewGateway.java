package com.platform.agent.hub.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ReviewGateway;
import com.platform.agent.hub.slack.SlackNotificationService;
import com.platform.common.agent.*;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.AgentReviewRequest;
import com.platform.core.domain.AgentWorkflowStep;
import com.platform.core.repository.AgentReviewRequestRepository;
import com.platform.core.repository.AgentWorkflowStepRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaReviewGateway implements ReviewGateway {

  private static final Logger log = LoggerFactory.getLogger(KafkaReviewGateway.class);

  private final KafkaTemplate<String, String> kafka;
  private final AgentReviewRequestRepository reviewRepo;
  private final AgentWorkflowStepRepository stepRepo;
  private final ObjectMapper mapper;

  @Autowired(required = false)
  private SlackNotificationService slackNotificationService;

  public KafkaReviewGateway(
      KafkaTemplate<String, String> kafka,
      AgentReviewRequestRepository reviewRepo,
      AgentWorkflowStepRepository stepRepo,
      ObjectMapper mapper) {
    this.kafka = kafka;
    this.reviewRepo = reviewRepo;
    this.stepRepo = stepRepo;
    this.mapper = mapper;
  }

  @Override
  public UUID requestReview(NodeResult result, ContextBundle bundle) {
    // Persist the review request
    AgentReviewRequest req =
        new AgentReviewRequest(
            result.workflowId(),
            findOrCreateStep(result),
            resolveChannel(bundle),
            resolveDestination(bundle),
            result.summary(),
            result.checkpointId(),
            Map.of("artifactCount", result.artifacts().artifacts().size()));
    AgentReviewRequest saved = reviewRepo.save(req);

    // Notify via Slack HITL (optional — skipped if bot-token not configured)
    if (slackNotificationService != null) {
      slackNotificationService.sendApprovalRequest(saved);
    }

    // Publish to Kafka — portal/Slack/GitHub consumers pick this up
    try {
      Map<String, Object> event = new HashMap<>();
      event.put("reviewRequestId", saved.getId().toString());
      event.put("workflowId", result.workflowId().toString());
      event.put("channel", saved.getChannel());
      event.put("destination", saved.getDestination());
      event.put("summary", result.summary());
      event.put(
          "createdAt",
          saved.getCreatedAt() != null
              ? saved.getCreatedAt().toString()
              : Instant.now().toString());
      kafka.send(
          Topics.AGENT_APPROVAL_REQUESTS,
          result.workflowId().toString(),
          mapper.writeValueAsString(event));
    } catch (Exception e) {
      log.error("failed to publish review request to Kafka", e);
    }

    log.info("review request created: {} channel={}", saved.getId(), saved.getChannel());
    return saved.getId();
  }

  @Override
  public void applyDecision(UUID reviewRequestId, ReviewDecision decision, String editedPayload) {
    reviewRepo
        .findById(reviewRequestId)
        .ifPresent(
            req -> {
              switch (decision) {
                case APPROVED -> req.approve("portal");
                case REJECTED -> req.reject("portal");
                case EDIT -> req.edit("portal", editedPayload);
                case DEFER -> req.defer(Instant.now().plusSeconds(86_400)); // +24 h
              }
              reviewRepo.save(req);
              log.info("review decision applied: {} → {}", reviewRequestId, decision);
            });
  }

  private String resolveChannel(ContextBundle bundle) {
    if (bundle.outboundTargets() == null || bundle.outboundTargets().reviewTarget() == null)
      return ReviewChannel.PORTAL.name();
    return bundle.outboundTargets().reviewTarget().channel().name();
  }

  private String resolveDestination(ContextBundle bundle) {
    if (bundle.outboundTargets() == null || bundle.outboundTargets().reviewTarget() == null)
      return "portal";
    return bundle.outboundTargets().reviewTarget().destination();
  }

  private UUID findOrCreateStep(NodeResult result) {
    return stepRepo.findByWorkflowIdAndStatus(result.workflowId(), "RUNNING").stream()
        .findFirst()
        .map(AgentWorkflowStep::getId)
        .orElse(UUID.randomUUID());
  }
}
