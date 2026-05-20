package com.platform.agent.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.agent.ReviewDecision;
import com.platform.common.kafka.Topics;
import com.platform.agent.hub.ReviewGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes human review decisions from agent.approval.decisions
 * and routes them to ReviewGateway.
 */
@Component
public class ApprovalDecisionConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApprovalDecisionConsumer.class);

    private final ReviewGateway reviewGateway;
    private final ObjectMapper  mapper;

    public ApprovalDecisionConsumer(ReviewGateway reviewGateway, ObjectMapper mapper) {
        this.reviewGateway = reviewGateway;
        this.mapper        = mapper;
    }

    @KafkaListener(topics = Topics.AGENT_APPROVAL_DECISIONS,
                   groupId = "${platform.agent.consumer-group:platform-agent}")
    public void onDecision(String payload) {
        try {
            JsonNode node = mapper.readTree(payload);
            UUID reviewRequestId = UUID.fromString(node.get("reviewRequestId").asText());
            ReviewDecision decision = ReviewDecision.valueOf(node.get("decision").asText());
            String editedPayload = node.has("editedPayload") ? node.get("editedPayload").asText() : null;

            reviewGateway.applyDecision(reviewRequestId, decision, editedPayload);
            log.info("approval decision applied: {} → {}", reviewRequestId, decision);
        } catch (Exception e) {
            log.error("failed to process approval decision: {}", payload, e);
        }
    }
}
