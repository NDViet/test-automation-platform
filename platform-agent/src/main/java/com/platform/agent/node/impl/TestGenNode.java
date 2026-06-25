package com.platform.agent.node.impl;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.common.agent.*;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.PlatformTraceabilityEdge;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.PlatformTraceabilityEdgeRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates test cases for a requirement using three modes:
 *
 * <p>CREATE_ALL — requirement has no coverage; generate new TCs from scratch. UPDATE_CHANGED —
 * existing TCs are NEEDS_UPDATE; patch them to match new ACs. REUSE_FROM_RELATED — candidates from
 * a related req cover ≥ 80% of ACs; link them.
 *
 * <p>Tools: save_test_cases, update_test_case, link_test_cases, request_review
 */
@Component
public class TestGenNode implements AgentNode {

  private static final Logger log = LoggerFactory.getLogger(TestGenNode.class);

  private final AgentOrchestrator orchestrator;
  private final PlatformTestCaseRepository testCaseRepo;
  private final PlatformTraceabilityEdgeRepository edgeRepo;
  private final ObjectMapper mapper;

  public TestGenNode(
      AgentOrchestrator orchestrator,
      PlatformTestCaseRepository testCaseRepo,
      PlatformTraceabilityEdgeRepository edgeRepo,
      ObjectMapper mapper) {
    this.orchestrator = orchestrator;
    this.testCaseRepo = testCaseRepo;
    this.edgeRepo = edgeRepo;
    this.mapper = mapper;
  }

  @Override
  public AgentTaskType taskType() {
    return AgentTaskType.GENERATE_AUTOMATED_TESTS;
  }

  @Override
  public NodeType nodeType() {
    return NodeType.TEST_GEN;
  }

  @Override
  public NodeResult execute(ContextBundle bundle) {
    return orchestrator.run(bundle, this);
  }

  @Override
  public List<Tool> tools() {
    return List.of(
        Tool.builder()
            .name("save_test_cases")
            .description(
                "Persist new test cases for a requirement. "
                    + "Use for CREATE_ALL and CREATE_FOR_NEW_ACS modes.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties",
                        JsonValue.from(
                            Map.of(
                                "requirement_id",
                                    Map.of(
                                        "type",
                                        "string",
                                        "description",
                                        "UUID of the requirement these TCs cover"),
                                "test_cases",
                                    Map.of(
                                        "type", "array",
                                        "description", "List of test cases to create",
                                        "items",
                                            Map.of(
                                                "type", "object",
                                                "properties",
                                                    Map.of(
                                                        "title", Map.of("type", "string"),
                                                        "ac_refs",
                                                            Map.of(
                                                                "type",
                                                                "array",
                                                                "items",
                                                                Map.of("type", "string")),
                                                        "has_automation",
                                                            Map.of("type", "boolean")),
                                                "required", List.of("title"))))))
                    .addRequired("requirement_id")
                    .addRequired("test_cases")
                    .build())
            .build(),
        Tool.builder()
            .name("update_test_case")
            .description(
                "Update an existing test case that is NEEDS_UPDATE. "
                    + "Use for UPDATE_CHANGED mode.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties",
                        JsonValue.from(
                            Map.of(
                                "test_case_id",
                                    Map.of(
                                        "type",
                                        "string",
                                        "description",
                                        "UUID of the test case to update"),
                                "new_title", Map.of("type", "string"),
                                "new_ac_refs",
                                    Map.of("type", "array", "items", Map.of("type", "string")))))
                    .addRequired("test_case_id")
                    .build())
            .build(),
        Tool.builder()
            .name("link_test_cases")
            .description(
                "Link existing test cases to a requirement via COVERED_BY edges. "
                    + "Use for REUSE_FROM_RELATED mode.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties",
                        JsonValue.from(
                            Map.of(
                                "requirement_id", Map.of("type", "string"),
                                "test_case_ids",
                                    Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        Map.of("type", "string"),
                                        "description",
                                        "UUIDs of existing test cases to link"))))
                    .addRequired("requirement_id")
                    .addRequired("test_case_ids")
                    .build())
            .build(),
        Tool.builder()
            .name("request_review")
            .description(
                "Pause and request human review of the proposed test cases "
                    + "before they are saved.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties",
                        JsonValue.from(
                            Map.of(
                                "summary", Map.of("type", "string"),
                                "payload", Map.of("type", "string"))))
                    .addRequired("summary")
                    .addRequired("payload")
                    .build())
            .build());
  }

  @Override
  @Transactional
  public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
    return switch (toolName) {
      case "save_test_cases" -> handleSaveTestCases(inputJson, bundle);
      case "update_test_case" -> handleUpdateTestCase(inputJson, bundle);
      case "link_test_cases" -> handleLinkTestCases(inputJson, bundle);
      case "request_review" -> handleRequestReview(inputJson, bundle);
      default -> "Unknown tool: " + toolName;
    };
  }

  // -------------------------------------------------------------------------

  private String handleSaveTestCases(String inputJson, ContextBundle bundle) {
    try {
      JsonNode input = mapper.readTree(inputJson);
      String reqIdStr = input.path("requirement_id").asText();
      JsonNode testCases = input.get("test_cases");
      if (testCases == null || !testCases.isArray()) return "Error: test_cases array required";

      UUID requirementId = UUID.fromString(reqIdStr);
      int count = 0;
      for (JsonNode tc : testCases) {
        String title = tc.path("title").asText();
        boolean hasAuto = tc.path("has_automation").asBoolean(false);
        List<String> acRefs =
            mapper.convertValue(
                tc.path("ac_refs"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));

        PlatformTestCase entity =
            new PlatformTestCase(bundle.projectId(), title, acRefs, "AGENT", bundle.sessionId());
        entity.setHasAutomation(hasAuto);
        PlatformTestCase saved = testCaseRepo.save(entity);

        // Create COVERED_BY edge: TC → REQUIREMENT
        PlatformTraceabilityEdge edge =
            new PlatformTraceabilityEdge(
                bundle.projectId(),
                saved.getId(),
                "TEST_CASE",
                requirementId,
                "REQUIREMENT",
                "COVERED_BY");
        edgeRepo.save(edge);
        count++;
      }
      log.info(
          "Saved {} test cases for requirement {} in project {}",
          count,
          reqIdStr,
          bundle.projectId());
      return "Saved " + count + " test cases successfully with COVERED_BY edges.";
    } catch (Exception e) {
      log.error("save_test_cases failed", e);
      return "Error saving test cases: " + e.getMessage();
    }
  }

  private String handleUpdateTestCase(String inputJson, ContextBundle bundle) {
    try {
      JsonNode input = mapper.readTree(inputJson);
      UUID tcId = UUID.fromString(input.path("test_case_id").asText());
      String newTitle = input.path("new_title").asText(null);
      JsonNode acRefsNode = input.get("new_ac_refs");

      return testCaseRepo
          .findById(tcId)
          .map(
              tc -> {
                boolean changed = false;
                if (newTitle != null && !newTitle.isBlank()) {
                  tc.setTitle(newTitle);
                  changed = true;
                }
                if (acRefsNode != null && acRefsNode.isArray()) {
                  List<String> refs =
                      mapper.convertValue(
                          acRefsNode,
                          mapper
                              .getTypeFactory()
                              .constructCollectionType(List.class, String.class));
                  tc.setAcRefs(refs);
                  changed = true;
                }
                if (changed) {
                  tc.markActive();
                  testCaseRepo.save(tc);
                  return "Updated test case " + tcId + " and marked ACTIVE.";
                }
                return "No changes applied to test case " + tcId;
              })
          .orElse("Test case not found: " + tcId);
    } catch (Exception e) {
      return "Error updating test case: " + e.getMessage();
    }
  }

  private String handleLinkTestCases(String inputJson, ContextBundle bundle) {
    try {
      JsonNode input = mapper.readTree(inputJson);
      UUID reqId = UUID.fromString(input.path("requirement_id").asText());
      JsonNode idsNode = input.get("test_case_ids");
      if (idsNode == null || !idsNode.isArray()) return "Error: test_case_ids array required";

      int linked = 0;
      for (JsonNode idNode : idsNode) {
        UUID tcId = UUID.fromString(idNode.asText());
        // Avoid duplicate edges
        if (edgeRepo
            .findByProjectIdAndFromIdAndToIdAndEdgeType(
                bundle.projectId(), tcId, reqId, "COVERED_BY")
            .isEmpty()) {
          PlatformTraceabilityEdge edge =
              new PlatformTraceabilityEdge(
                  bundle.projectId(), tcId, "TEST_CASE", reqId, "REQUIREMENT", "COVERED_BY");
          edgeRepo.save(edge);
          linked++;
        }
      }
      return "Linked " + linked + " existing test cases to requirement " + reqId;
    } catch (Exception e) {
      return "Error linking test cases: " + e.getMessage();
    }
  }

  private String handleRequestReview(String inputJson, ContextBundle bundle) {
    try {
      JsonNode input = mapper.readTree(inputJson);
      String payload = input.path("payload").asText("");
      return "__AWAITING_REVIEW__ " + payload;
    } catch (Exception e) {
      return "Error processing review request: " + e.getMessage();
    }
  }
}
