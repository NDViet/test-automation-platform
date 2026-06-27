package com.platform.agent.node.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.common.agent.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
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
  public List<ToolSpecification> toolSpecs() {
    return List.of(
        ToolSpecification.builder()
            .name("store_acceptance_criteria")
            .description(
                "Store the extracted acceptance criteria for the requirement, as Given/When/Then.")
            .parameters(
                JsonObjectSchema.builder()
                    .addProperty(
                        "criteria",
                        JsonArraySchema.builder()
                            .description("List of acceptance criteria as Given/When/Then")
                            .items(
                                JsonObjectSchema.builder()
                                    .addStringProperty("id")
                                    .addStringProperty("given")
                                    .addStringProperty("when")
                                    .addStringProperty("then")
                                    .addProperty(
                                        "priority",
                                        JsonEnumSchema.builder()
                                            .enumValues("MUST", "SHOULD", "COULD")
                                            .build())
                                    .build())
                            .build())
                    .required("criteria")
                    .build())
            .build(),
        ToolSpecification.builder()
            .name("request_review")
            .description("Pause execution and request human review.")
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("summary", "Brief description of what is being reviewed")
                    .addStringProperty("payload", "Full details for the reviewer")
                    .required("summary", "payload")
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
