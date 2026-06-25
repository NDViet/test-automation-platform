package com.platform.agent.node.impl;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.common.agent.*;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Node that extracts structured acceptance criteria from a requirement description. Tools available
 * to Claude: - store_acceptance_criteria: persist the extracted ACs as structured JSON -
 * request_review: pause and send the AC draft to a human reviewer
 */
@Component
public class ExtractAcceptanceCriteriaNode implements AgentNode {

  private static final Logger log = LoggerFactory.getLogger(ExtractAcceptanceCriteriaNode.class);

  private final AgentOrchestrator orchestrator;
  private final ObjectMapper mapper;

  public ExtractAcceptanceCriteriaNode(AgentOrchestrator orchestrator, ObjectMapper mapper) {
    this.orchestrator = orchestrator;
    this.mapper = mapper;
  }

  @Override
  public AgentTaskType taskType() {
    return AgentTaskType.EXTRACT_ACCEPTANCE_CRITERIA;
  }

  @Override
  public NodeType nodeType() {
    return NodeType.REQUIREMENT;
  }

  @Override
  public NodeResult execute(ContextBundle bundle) {
    return orchestrator.run(bundle, this);
  }

  @Override
  public List<Tool> tools() {
    return List.of(
        Tool.builder()
            .name("store_acceptance_criteria")
            .description(
                "Store the extracted acceptance criteria for the requirement. "
                    + "Call this once you have identified all ACs from the description.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties",
                        JsonValue.from(
                            Map.of(
                                "criteria",
                                Map.of(
                                    "type", "array",
                                    "description", "List of acceptance criteria as Given/When/Then",
                                    "items",
                                        Map.of(
                                            "type",
                                            "object",
                                            "properties",
                                            Map.of(
                                                "id", Map.of("type", "string"),
                                                "given", Map.of("type", "string"),
                                                "when", Map.of("type", "string"),
                                                "then", Map.of("type", "string"),
                                                "priority",
                                                    Map.of(
                                                        "type",
                                                        "string",
                                                        "enum",
                                                        List.of("MUST", "SHOULD", "COULD"))))))))
                    .addRequired("criteria")
                    .build())
            .build(),
        Tool.builder()
            .name("request_review")
            .description(
                "Pause execution and send the AC draft to a human reviewer "
                    + "when you need QA engineer validation before proceeding.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties",
                        JsonValue.from(
                            Map.of(
                                "summary",
                                    Map.of(
                                        "type",
                                        "string",
                                        "description",
                                        "Brief description of what is being reviewed"),
                                "payload",
                                    Map.of(
                                        "type",
                                        "string",
                                        "description",
                                        "The full draft content for review"))))
                    .addRequired("summary")
                    .addRequired("payload")
                    .build())
            .build());
  }

  @Override
  public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
    return switch (toolName) {
      case "store_acceptance_criteria" -> handleStoreAcs(inputJson, bundle);
      case "request_review" -> handleRequestReview(inputJson, bundle);
      default -> "Unknown tool: " + toolName;
    };
  }

  // -------------------------------------------------------------------------

  private String handleStoreAcs(String inputJson, ContextBundle bundle) {
    try {
      JsonNode input = mapper.readTree(inputJson);
      JsonNode criteria = input.get("criteria");
      if (criteria == null || !criteria.isArray()) {
        return "Error: 'criteria' array is required";
      }
      int count = criteria.size();
      log.info("stored {} acceptance criteria for project {}", count, bundle.projectId());
      return "Stored " + count + " acceptance criteria successfully.";
    } catch (Exception e) {
      log.error("failed to store ACs", e);
      return "Error storing acceptance criteria: " + e.getMessage();
    }
  }

  private String handleRequestReview(String inputJson, ContextBundle bundle) {
    try {
      JsonNode input = mapper.readTree(inputJson);
      String summary = input.path("summary").asText("AC extraction review");
      String payload = input.path("payload").asText("");
      log.info("requesting review for session {}: {}", bundle.sessionId(), summary);
      // Sentinel prefix tells ClaudeAgentOrchestrator to pause for HITL
      return "__AWAITING_REVIEW__ " + payload;
    } catch (Exception e) {
      return "Error processing review request: " + e.getMessage();
    }
  }
}
