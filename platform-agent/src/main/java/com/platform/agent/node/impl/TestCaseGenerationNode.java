package com.platform.agent.node.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.common.agent.*;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseStep;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseStepRepository;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TEST_GENERATION node that generates manual test cases from project requirements.
 *
 * <p>This node bypasses the Claude tool-use loop and instead: 1. Loads requirements from the DB 2.
 * Calls Claude once via the orchestrator to get a JSON array of test case specs 3. Persists each
 * generated test case + its steps directly
 *
 * <p>The orchestrator is invoked through a thin shim node that overrides execute() so Claude gets a
 * structured prompt and returns pure JSON (no tool calls needed).
 */
@Component
public class TestCaseGenerationNode implements AgentNode {

  private static final Logger log = LoggerFactory.getLogger(TestCaseGenerationNode.class);

  private final AgentOrchestrator orchestrator;
  private final PlatformRequirementRepository requirementRepo;
  private final PlatformTestCaseRepository testCaseRepo;
  private final TestCaseStepRepository stepRepo;
  private final ObjectMapper mapper;

  public TestCaseGenerationNode(
      AgentOrchestrator orchestrator,
      PlatformRequirementRepository requirementRepo,
      PlatformTestCaseRepository testCaseRepo,
      TestCaseStepRepository stepRepo,
      ObjectMapper mapper) {
    this.orchestrator = orchestrator;
    this.requirementRepo = requirementRepo;
    this.testCaseRepo = testCaseRepo;
    this.stepRepo = stepRepo;
    this.mapper = mapper;
  }

  @Override
  public AgentTaskType taskType() {
    return AgentTaskType.GENERATE_TEST_CASES;
  }

  @Override
  public NodeType nodeType() {
    return NodeType.TEST_GENERATION;
  }

  @Override
  public String systemPrompt(ContextBundle bundle) {
    return """
    You are the TestCaseGenerationNode — a QA expert that creates thorough manual test cases from product requirements.

    Project: %s (ID: %s)

    ## Your job
    Given a list of requirements (user stories, epics, tasks) with acceptance criteria, generate comprehensive manual test cases.

    ## Rules
    - Each test case must map to one requirement (use its ID as sourceRequirementId)
    - Cover happy path, edge cases, and error conditions
    - Write clear, executable steps (action + expected result per step)
    - Set priority: CRITICAL for core flows, HIGH for important features, MEDIUM for standard, LOW for edge cases
    - Response MUST be valid JSON — an array of test case objects

    ## Output format (JSON array):
    [
      {
        "title": "...",
        "description": "...",
        "preconditions": "...",
        "priority": "HIGH",
        "sourceRequirementId": "uuid-of-requirement",
        "steps": [
          { "action": "...", "expectedResult": "...", "notes": null }
        ]
      }
    ]
    """
        .formatted(bundle.projectSlug(), bundle.projectId());
  }

  @Override
  @Transactional
  public NodeResult execute(ContextBundle bundle) {
    UUID projectId = bundle.projectId();

    // 1. Load requirements for this project
    List<PlatformRequirement> allRequirements =
        requirementRepo.findByProjectIdOrderByUpdatedAtDesc(projectId);

    // 2. Filter by requirementIds if provided in the trigger's entityExternalId
    //    Convention: entityExternalId = comma-separated UUID list, or the project ID itself
    //    when generating for all requirements.
    List<PlatformRequirement> requirements = filterRequirements(allRequirements, bundle);

    if (requirements.isEmpty()) {
      log.warn("TestCaseGenerationNode: no requirements found for project {}", projectId);
      return NodeResult.completed(
          bundle.sessionId(),
          bundle.workflowId(),
          nodeType(),
          taskType(),
          ArtifactManifest.empty(),
          "No requirements found for project " + projectId + ". No test cases generated.",
          TokenUsage.zero());
    }

    // 3. Build user message listing all requirements
    String userMessage = buildRequirementsMessage(requirements);

    // 4. Use a thin shim to pass the pre-built user message to Claude
    //    We wrap ourselves as a shim so the orchestrator sends our system prompt + user message
    TextOnlyNode shim = new TextOnlyNode(this, userMessage);
    NodeResult claudeResult = orchestrator.run(bundle, shim);

    if (claudeResult.hasFailed()) {
      return claudeResult;
    }

    // 5. Parse the JSON array from Claude's response
    String responseText = claudeResult.summary() != null ? claudeResult.summary() : "";
    List<GeneratedTestCase> generatedCases = parseGeneratedTestCases(responseText);

    if (generatedCases.isEmpty()) {
      log.warn(
          "TestCaseGenerationNode: Claude returned no parseable test cases for project {}",
          projectId);
      return NodeResult.completed(
          bundle.sessionId(),
          bundle.workflowId(),
          nodeType(),
          taskType(),
          ArtifactManifest.empty(),
          "Generated 0 test cases from "
              + requirements.size()
              + " requirements (parse error or empty response).",
          claudeResult.tokenUsage());
    }

    // 6. Persist each generated test case and its steps
    int savedCount = 0;
    for (GeneratedTestCase gen : generatedCases) {
      try {
        PlatformTestCase tc =
            new PlatformTestCase(projectId, gen.title(), List.of(), "AGENT", bundle.workflowId());
        tc.setDescription(gen.description());
        tc.setPreconditions(gen.preconditions());
        tc.setExpectedResult(gen.expectedResult());
        tc.setPriority(normalizePriority(gen.priority()));
        if (gen.sourceRequirementId() != null) {
          try {
            UUID reqId = UUID.fromString(gen.sourceRequirementId());
            tc.setSourceRequirementId(reqId);
            tc.linkRequirement(reqId); // seed linked_requirement_ids
          } catch (IllegalArgumentException e) {
            log.debug(
                "TestCaseGenerationNode: invalid sourceRequirementId '{}', ignoring",
                gen.sourceRequirementId());
          }
        }
        tc.setStatus("DRAFT");
        PlatformTestCase saved = testCaseRepo.save(tc);

        // Persist steps
        if (gen.steps() != null) {
          int stepNum = 1;
          for (GeneratedStep step : gen.steps()) {
            TestCaseStep stepEntity =
                new TestCaseStep(
                    saved.getId(), stepNum++, step.action(), step.expectedResult(), step.notes());
            stepRepo.save(stepEntity);
          }
        }
        savedCount++;
      } catch (Exception e) {
        log.error(
            "TestCaseGenerationNode: failed to save test case '{}': {}",
            gen.title(),
            e.getMessage(),
            e);
      }
    }

    String summary =
        "Generated " + savedCount + " test cases from " + requirements.size() + " requirements.";
    log.info("TestCaseGenerationNode: {} for project {}", summary, projectId);

    return NodeResult.completed(
        bundle.sessionId(),
        bundle.workflowId(),
        nodeType(),
        taskType(),
        ArtifactManifest.empty(),
        summary,
        claudeResult.tokenUsage());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Filter requirements to only those whose IDs are listed in the trigger. If the trigger's
   * entityExternalId equals the project ID (all-requirements case) or is null/blank, return all
   * requirements unfiltered.
   */
  private List<PlatformRequirement> filterRequirements(
      List<PlatformRequirement> all, ContextBundle bundle) {
    TriggerRef trigger = bundle.trigger();
    if (trigger == null) return all;

    String extId = trigger.entityExternalId();
    // extId is the project UUID when generating for all requirements
    if (extId == null || extId.isBlank() || extId.equals(bundle.projectId().toString())) {
      return all;
    }

    // extId may be a comma-separated list of requirement UUIDs
    Set<UUID> filterIds = new HashSet<>();
    for (String part : extId.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isBlank()) {
        try {
          filterIds.add(UUID.fromString(trimmed));
        } catch (IllegalArgumentException ignored) {
          // not a UUID — could be the project ID string; fall through to return all
          return all;
        }
      }
    }

    if (filterIds.isEmpty()) return all;

    return all.stream().filter(r -> filterIds.contains(r.getId())).toList();
  }

  private String buildRequirementsMessage(List<PlatformRequirement> requirements) {
    // Load existing test cases for these requirements to avoid duplication
    Set<UUID> reqIds =
        requirements.stream()
            .map(PlatformRequirement::getId)
            .collect(java.util.stream.Collectors.toSet());
    List<PlatformTestCase> existing =
        requirements.isEmpty()
            ? List.of()
            : testCaseRepo.findByProjectId(requirements.get(0).getProjectId()).stream()
                .filter(
                    tc ->
                        tc.getSourceRequirementId() != null
                            && reqIds.contains(tc.getSourceRequirementId()))
                .toList();

    StringBuilder sb = new StringBuilder();
    sb.append("Generate comprehensive manual test cases for the following requirements:\n\n");
    for (PlatformRequirement req : requirements) {
      sb.append("---\n");
      sb.append("Requirement ID: ").append(req.getId()).append("\n");
      sb.append("Type: ").append(req.getIssueType()).append("\n");
      sb.append("Title: ").append(req.getTitle()).append("\n");
      if (req.getDescription() != null && !req.getDescription().isBlank()) {
        sb.append("Description: ").append(req.getDescription().trim()).append("\n");
      }
      if (req.getAcceptanceCriteria() != null && !req.getAcceptanceCriteria().isEmpty()) {
        sb.append("Acceptance Criteria:\n");
        for (Object ac : req.getAcceptanceCriteria()) {
          sb.append("  - ").append(ac).append("\n");
        }
      }
      // Show existing coverage so Claude doesn't duplicate
      List<PlatformTestCase> covered =
          existing.stream().filter(tc -> req.getId().equals(tc.getSourceRequirementId())).toList();
      if (!covered.isEmpty()) {
        sb.append("Already covered by test cases (do NOT duplicate these):\n");
        for (PlatformTestCase tc : covered) {
          sb.append("  - [").append(tc.getStatus()).append("] ").append(tc.getTitle()).append("\n");
        }
      }
      sb.append("\n");
    }
    sb.append("---\n");
    sb.append(
        "Return ONLY a valid JSON array of test case objects. No prose before or after the JSON.");
    return sb.toString();
  }

  private List<GeneratedTestCase> parseGeneratedTestCases(String responseText) {
    if (responseText == null || responseText.isBlank()) return List.of();

    // Extract the JSON array from the response (Claude may wrap it in markdown code blocks)
    String json = extractJsonArray(responseText);
    if (json == null) {
      log.warn(
          "TestCaseGenerationNode: could not extract JSON array from response: {}",
          responseText.substring(0, Math.min(200, responseText.length())));
      return List.of();
    }

    try {
      JsonNode array = mapper.readTree(json);
      if (!array.isArray()) {
        log.warn("TestCaseGenerationNode: expected JSON array, got: {}", array.getNodeType());
        return List.of();
      }

      List<GeneratedTestCase> result = new ArrayList<>();
      for (JsonNode node : array) {
        try {
          result.add(parseTestCaseNode(node));
        } catch (Exception e) {
          log.warn(
              "TestCaseGenerationNode: skipping unparseable test case node: {}", e.getMessage());
        }
      }
      return result;
    } catch (Exception e) {
      log.warn("TestCaseGenerationNode: JSON parse error: {}", e.getMessage());
      return List.of();
    }
  }

  private GeneratedTestCase parseTestCaseNode(JsonNode node) {
    String title = node.path("title").asText(null);
    String description = node.path("description").asText(null);
    String preconditions = node.path("preconditions").asText(null);
    String expectedResult =
        node.path("expectedResult").asText(node.path("expected_result").asText(null));
    String priority = node.path("priority").asText("MEDIUM");
    String sourceReqId =
        node.path("sourceRequirementId").asText(node.path("source_requirement_id").asText(null));

    List<GeneratedStep> steps = new ArrayList<>();
    JsonNode stepsNode = node.path("steps");
    if (stepsNode.isArray()) {
      for (JsonNode stepNode : stepsNode) {
        String action = stepNode.path("action").asText(null);
        String expResult =
            stepNode.path("expectedResult").asText(stepNode.path("expected_result").asText(null));
        String notes = stepNode.path("notes").isNull() ? null : stepNode.path("notes").asText(null);
        if (action != null) {
          steps.add(new GeneratedStep(action, expResult, notes));
        }
      }
    }

    return new GeneratedTestCase(
        title, description, preconditions, expectedResult, priority, sourceReqId, steps);
  }

  /**
   * Extracts the first JSON array from the response text, handling markdown code fences (```json
   * ... ```) and bare arrays.
   */
  private String extractJsonArray(String text) {
    // Try markdown fence first
    int fenceStart = text.indexOf("```");
    if (fenceStart != -1) {
      int arrayStart = text.indexOf('[', fenceStart);
      if (arrayStart != -1) {
        int fenceEnd = text.indexOf("```", arrayStart);
        if (fenceEnd != -1) {
          return text.substring(arrayStart, fenceEnd).trim();
        }
      }
    }

    // Try bare JSON array
    int start = text.indexOf('[');
    if (start != -1) {
      int end = text.lastIndexOf(']');
      if (end > start) {
        return text.substring(start, end + 1).trim();
      }
    }
    return null;
  }

  private String normalizePriority(String raw) {
    if (raw == null) return "MEDIUM";
    return switch (raw.toUpperCase().trim()) {
      case "CRITICAL" -> "CRITICAL";
      case "HIGH" -> "HIGH";
      case "LOW" -> "LOW";
      default -> "MEDIUM";
    };
  }

  // -------------------------------------------------------------------------
  // Internal value types
  // -------------------------------------------------------------------------

  private record GeneratedTestCase(
      String title,
      String description,
      String preconditions,
      String expectedResult,
      String priority,
      String sourceRequirementId,
      List<GeneratedStep> steps) {}

  private record GeneratedStep(String action, String expectedResult, String notes) {}

  // -------------------------------------------------------------------------
  // Shim: wraps this node but replaces the user prompt so Claude receives the
  // pre-built requirements message instead of the generic context bundle prompt.
  // -------------------------------------------------------------------------

  /**
   * A thin wrapper AgentNode that replaces the user message seen by the orchestrator. The
   * orchestrator calls buildUserPrompt(bundle) internally; we can't override that directly, so we
   * supply a custom systemPrompt that embeds the requirements and instructs Claude to respond with
   * pure JSON — the orchestrator's generic user prompt ("Task: GENERATE_TEST_CASES") becomes
   * harmless.
   */
  private static class TextOnlyNode implements AgentNode {
    private final TestCaseGenerationNode parent;
    private final String requirementsMessage;

    TextOnlyNode(TestCaseGenerationNode parent, String requirementsMessage) {
      this.parent = parent;
      this.requirementsMessage = requirementsMessage;
    }

    @Override
    public AgentTaskType taskType() {
      return parent.taskType();
    }

    @Override
    public NodeType nodeType() {
      return parent.nodeType();
    }

    @Override
    public String systemPrompt(ContextBundle bundle) {
      // Combine the node's system prompt with the requirements message so the
      // orchestrator's generic user message ("Task: GENERATE_TEST_CASES") is supplementary.
      return parent.systemPrompt(bundle)
          + "\n\n## Requirements to process\n\n"
          + requirementsMessage;
    }

    @Override
    public NodeResult execute(ContextBundle bundle) {
      return parent.orchestrator.run(bundle, this);
    }

    @Override
    public List<ToolSpecification> toolSpecs() {
      return List.of();
    }

    @Override
    public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
      return "No tools available for this node.";
    }
  }
}
