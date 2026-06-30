package com.platform.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.agents.EffectiveAgentConfig;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.agent.workflow.GenerationResumeService;
import com.platform.agent.workflow.GenerationStatusService;
import com.platform.common.agent.AgentTaskType;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.GenerateTestCasesRequest;
import com.platform.common.agent.TriggerRef;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.AiGenerationRun;
import com.platform.core.repository.AiGenerationRunRepository;
import com.platform.security.authz.Capability;
import com.platform.security.web.CurrentUser;
import com.platform.security.web.RequireCapability;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Triggers TCM AI generation workflows:
 *
 * <p>POST /hub/test-cases/{projectId}/generate Body (optional): { "requirementIds": ["uuid1",
 * "uuid2"] } Triggers a GENERATE_TEST_CASES workflow for the project. If requirementIds is absent,
 * generates test cases for all requirements in the project.
 *
 * <p>POST /hub/test-cases/{projectId}/{testCaseId}/generate-automation Body (optional): {
 * "githubConfigId": "uuid" } Triggers a GENERATE_AUTOMATION_CODE workflow for the given test case.
 */
@RestController
@RequestMapping("/hub/test-cases")
public class TestCaseGenerationController {

  private static final Logger log = LoggerFactory.getLogger(TestCaseGenerationController.class);

  private final AgentWorkflowService workflowService;
  private final ContextAssembler contextAssembler;
  private final ObjectMapper mapper;
  private final GenerationInputService inputService;
  private final AiGenerationRunRepository runRepo;
  private final GenerationResumeService resumeService;
  private final GenerationStatusService statusService;
  private final com.platform.agent.agents.AgentResolutionService agentResolutionService;
  private final com.platform.agent.proposals.ProposalService proposalService;

  public TestCaseGenerationController(
      AgentWorkflowService workflowService,
      ContextAssembler contextAssembler,
      ObjectMapper mapper,
      GenerationInputService inputService,
      AiGenerationRunRepository runRepo,
      GenerationResumeService resumeService,
      GenerationStatusService statusService,
      com.platform.agent.agents.AgentResolutionService agentResolutionService,
      com.platform.agent.proposals.ProposalService proposalService) {
    this.workflowService = workflowService;
    this.contextAssembler = contextAssembler;
    this.mapper = mapper;
    this.inputService = inputService;
    this.runRepo = runRepo;
    this.resumeService = resumeService;
    this.statusService = statusService;
    this.agentResolutionService = agentResolutionService;
    this.proposalService = proposalService;
  }

  /**
   * POST /hub/test-cases/{projectId}/generate
   *
   * <p>Triggers a GENERATE_TEST_CASES workflow.
   *
   * <p>Body (optional JSON): { "requirementIds": ["uuid1", "uuid2", ...] }
   *
   * <p>When requirementIds is absent or empty, test cases are generated for all requirements
   * associated with the project.
   *
   * <p>The requirementIds list is encoded in the trigger's entityExternalId as a comma-separated
   * string of UUIDs. When generating for all requirements the project UUID is used, which the
   * TestCaseGenerationNode treats as "all".
   */
  @PostMapping("/{projectId}/generate")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Map<String, Object>> generateTestCases(
      @PathVariable UUID projectId, @RequestBody(required = false) String body) {

    log.info("Test case generation requested for project {}", projectId);

    // Parse the rich request. Legacy bodies ({"requirementIds":[...]}) and an absent body both
    // map cleanly: missing fields are null, so the original one-shot behavior is preserved.
    GenerateTestCasesRequest req = parseRequest(body);
    List<String> requirementIds = req.requirementIdsOrEmpty();

    // A completely empty request = legacy "generate for all requirements" — no input/skills/
    // overrides to validate or persist. Anything richer goes through validation.
    boolean isNewFlow = hasNewFlowData(req);
    if (req.hasAnyInput() || isNewFlow) {
      inputService.validate(projectId, req);
    }

    // Build entityExternalId: comma-separated UUIDs if filtered, else project UUID
    String entityExternalId =
        requirementIds.isEmpty() ? projectId.toString() : String.join(",", requirementIds);

    TriggerRef trigger =
        new TriggerRef(
            TriggerRef.TriggerType.MANUAL,
            null,
            "generate_test_cases",
            entityExternalId,
            null,
            null,
            Instant.now());

    try {
      AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);

      // Resolve the agent for this run (explicit pick → project/org assignment → built-in seed).
      // The seed reproduces today's default prompts, so an unconfigured run behaves as before.
      UUID explicitAgentId = parseUuidOrNull(req.agentId());
      EffectiveAgentConfig cfg =
          agentResolutionService.resolve(
              projectId, AgentTaskType.GENERATE_TEST_CASES, req.subType(), explicitAgentId);
      String effectiveSubType =
          agentResolutionService.effectiveSubType(AgentTaskType.GENERATE_TEST_CASES, req.subType());

      // Persist the resolved run inputs so the node assembles from the agent's
      // prompts/model/rounds,
      // plus any rich per-run input (free text, files). An explicit user prompt override still
      // wins.
      int rounds =
          req.maxRounds() != null
              ? clampRounds(req.maxRounds())
              : (isNewFlow ? 3 : cfg.maxRounds());
      AiGenerationRun run =
          new AiGenerationRun(
              workflow.getId(),
              projectId,
              writeJson(req.skillIdsOrEmpty()),
              req.hasFreeText() ? req.freeText() : null,
              writeJson(req.fileIdsOrEmpty()),
              notBlank(req.systemPromptOverride())
                  ? req.systemPromptOverride()
                  : cfg.systemPrompt(),
              notBlank(req.userPromptOverride()) ? req.userPromptOverride() : cfg.userPrompt(),
              rounds);
      run.recordAgentResolution(cfg.agentId(), effectiveSubType, cfg.modelId());
      runRepo.save(run);

      ContextBundle bundle = contextAssembler.assemble(workflow.getId(), projectId, trigger);
      workflowService.executeWorkflow(workflow.getId(), bundle);

      String message =
          requirementIds.isEmpty()
              ? "Test case generation started for all requirements."
              : "Test case generation started for " + requirementIds.size() + " requirement(s).";

      log.info(
          "Test case generation workflow {} started for project {}", workflow.getId(), projectId);

      return ResponseEntity.accepted()
          .body(
              Map.of(
                  "workflowId", workflow.getId().toString(),
                  "projectId", projectId.toString(),
                  "message", message));
    } catch (Exception e) {
      log.error(
          "Failed to start test case generation for project {}: {}", projectId, e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Failed to start test case generation: " + e.getMessage()));
    }
  }

  /**
   * GET /hub/test-cases/{projectId}/generations/{workflowId}
   *
   * <p>Run status + clarification transcript + the currently pending question round (if any).
   */
  @GetMapping("/{projectId}/generations/{workflowId}")
  @RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
  public GenerationStatusService.GenerationStatusDto getGeneration(
      @PathVariable UUID projectId, @PathVariable UUID workflowId) {
    return statusService.getStatus(projectId, workflowId);
  }

  /**
   * POST /hub/test-cases/{projectId}/generations/{workflowId}/answers
   *
   * <p>Submit answers to the agent's clarifying questions and resume the paused run. Returns 409 if
   * the run is not awaiting input. Body: {@code { "answers": [{ "id": "...", "answer": "..." }] }}.
   */
  @PostMapping("/{projectId}/generations/{workflowId}/answers")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Map<String, Object>> submitAnswers(
      @PathVariable UUID projectId,
      @PathVariable UUID workflowId,
      @RequestBody(required = false) String body) {
    List<GenerationResumeService.Answer> answers = parseAnswers(body);
    GenerationResumeService.ResumePlan plan =
        resumeService.markAnswered(projectId, workflowId, answers);
    boolean allowMore = resumeService.allowMoreQuestions(workflowId);
    resumeService.resumeAsync(workflowId, plan, allowMore);
    return ResponseEntity.accepted()
        .body(
            Map.of(
                "workflowId", workflowId.toString(),
                "projectId", projectId.toString(),
                "status", "RESUMING"));
  }

  /**
   * GET /hub/test-cases/{projectId}/generations/{workflowId}/proposals
   *
   * <p>The AI-generated test cases staged for review (status PROPOSED/ACCEPTED/REJECTED). The user
   * accepts / rejects / refines these before any enter the catalog.
   */
  @GetMapping("/{projectId}/generations/{workflowId}/proposals")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public List<com.platform.agent.proposals.ProposalDtos.ProposalDto> listProposals(
      @PathVariable UUID projectId, @PathVariable UUID workflowId) {
    return proposalService.list(projectId, workflowId);
  }

  /** Accept a proposal → it becomes a DRAFT test case in the catalog. */
  @PostMapping("/{projectId}/generations/{workflowId}/proposals/{proposalId}/accept")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public com.platform.agent.proposals.ProposalDtos.ProposalDto acceptProposal(
      @PathVariable UUID projectId, @PathVariable UUID workflowId, @PathVariable UUID proposalId) {
    return proposalService.accept(projectId, proposalId, CurrentUser.username());
  }

  /** Accept every still-proposed case in the run. */
  @PostMapping("/{projectId}/generations/{workflowId}/proposals/accept-all")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public List<com.platform.agent.proposals.ProposalDtos.ProposalDto> acceptAllProposals(
      @PathVariable UUID projectId, @PathVariable UUID workflowId) {
    return proposalService.acceptAll(projectId, workflowId, CurrentUser.username());
  }

  /** Reject (discard) a proposal — it never enters the catalog. */
  @PostMapping("/{projectId}/generations/{workflowId}/proposals/{proposalId}/reject")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public com.platform.agent.proposals.ProposalDtos.ProposalDto rejectProposal(
      @PathVariable UUID projectId, @PathVariable UUID workflowId, @PathVariable UUID proposalId) {
    return proposalService.reject(projectId, proposalId);
  }

  /**
   * Refine a proposal: the AI revises it in place by continuing the generation conversation. Async
   * — progress streams on the generation socket; re-fetch proposals when the run returns to review.
   */
  @PostMapping("/{projectId}/generations/{workflowId}/proposals/{proposalId}/refine")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Map<String, Object>> refineProposal(
      @PathVariable UUID projectId,
      @PathVariable UUID workflowId,
      @PathVariable UUID proposalId,
      @RequestBody(required = false) com.platform.agent.proposals.ProposalDtos.RefineRequest body) {
    GenerationResumeService.RefinePlan plan =
        resumeService.validateRefine(
            projectId, workflowId, proposalId, body != null ? body.instruction() : null);
    resumeService.refineAsync(workflowId, plan);
    return ResponseEntity.accepted()
        .body(
            Map.of(
                "workflowId", workflowId.toString(),
                "proposalId", proposalId.toString(),
                "status", "REFINING"));
  }

  /**
   * Refine every still-proposed case in the run with one instruction (AI revises each in place).
   */
  @PostMapping("/{projectId}/generations/{workflowId}/proposals/refine-all")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Map<String, Object>> refineAllProposals(
      @PathVariable UUID projectId,
      @PathVariable UUID workflowId,
      @RequestBody(required = false) com.platform.agent.proposals.ProposalDtos.RefineRequest body) {
    GenerationResumeService.RefineAllPlan plan =
        resumeService.validateRefineAll(
            projectId, workflowId, body != null ? body.instruction() : null);
    resumeService.refineAllAsync(plan);
    return ResponseEntity.accepted()
        .body(Map.of("workflowId", workflowId.toString(), "status", "REFINING"));
  }

  /**
   * POST /hub/test-cases/{projectId}/{testCaseId}/generate-automation
   *
   * <p>Triggers a GENERATE_AUTOMATION_CODE workflow for the given test case.
   *
   * <p>Body (optional JSON): { "githubConfigId": "uuid" }
   *
   * <p>When githubConfigId is provided, it is stored in the test case's automationGithubConfigId
   * field before the workflow starts. The AutomationCodeGenerationNode reads it to prefer that
   * GitHub config.
   */
  @PostMapping("/{projectId}/{testCaseId}/generate-automation")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Map<String, Object>> generateAutomation(
      @PathVariable UUID projectId,
      @PathVariable UUID testCaseId,
      @RequestBody(required = false) String body) {

    log.info(
        "Automation code generation requested for test case {} in project {}",
        testCaseId,
        projectId);

    // Parse optional githubConfigId + agent selection
    UUID githubConfigId = parseGithubConfigId(body);
    UUID explicitAgentId = parseUuidOrNull(jsonText(body, "agentId"));
    String subType = jsonText(body, "subType");

    TriggerRef trigger =
        new TriggerRef(
            TriggerRef.TriggerType.MANUAL,
            null,
            "generate_automation_code",
            testCaseId.toString(),
            null,
            null,
            Instant.now());

    try {
      AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);

      // Resolve the agent for automation generation (explicit → assignment → seed) and record it
      // on a run so the node can apply the model override. The node keeps its specialized prompt.
      EffectiveAgentConfig cfg =
          agentResolutionService.resolve(
              projectId, AgentTaskType.GENERATE_AUTOMATION_CODE, subType, explicitAgentId);
      String effectiveSubType =
          agentResolutionService.effectiveSubType(AgentTaskType.GENERATE_AUTOMATION_CODE, subType);
      AiGenerationRun run =
          new AiGenerationRun(workflow.getId(), projectId, "[]", null, "[]", null, null, 0);
      run.recordAgentResolution(cfg.agentId(), effectiveSubType, cfg.modelId());
      runRepo.save(run);

      ContextBundle bundle = contextAssembler.assemble(workflow.getId(), projectId, trigger);
      workflowService.executeWorkflow(workflow.getId(), bundle);

      Map<String, Object> response = new java.util.LinkedHashMap<>();
      response.put("workflowId", workflow.getId().toString());
      response.put("projectId", projectId.toString());
      response.put("testCaseId", testCaseId.toString());
      response.put("message", "Automation generation started.");
      if (githubConfigId != null) {
        response.put("githubConfigId", githubConfigId.toString());
      }

      log.info(
          "Automation generation workflow {} started for test case {} in project {}",
          workflow.getId(),
          testCaseId,
          projectId);

      return ResponseEntity.accepted().body(response);
    } catch (Exception e) {
      log.error(
          "Failed to start automation generation for test case {} in project {}: {}",
          testCaseId,
          projectId,
          e.getMessage(),
          e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Failed to start automation generation: " + e.getMessage()));
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Parse the optional request body into a {@link GenerateTestCasesRequest}. An absent/blank body
   * or a legacy {@code {"requirementIds":[...]}} body both map cleanly (missing fields → null).
   */
  private GenerateTestCasesRequest parseRequest(String body) {
    if (body == null || body.isBlank()) {
      return new GenerateTestCasesRequest(
          List.of(), null, null, null, null, null, null, null, null);
    }
    try {
      JsonNode root = mapper.readTree(body);
      return new GenerateTestCasesRequest(
          stringList(root.path("requirementIds")),
          textOrNull(root.path("freeText")),
          stringList(root.path("fileIds")),
          stringList(root.path("skillIds")),
          textOrNull(root.path("systemPromptOverride")),
          textOrNull(root.path("userPromptOverride")),
          root.path("maxRounds").isInt() ? root.path("maxRounds").asInt() : null,
          textOrNull(root.path("agentId")),
          textOrNull(root.path("subType")));
    } catch (Exception e) {
      log.debug("Could not parse generation request body: {}", e.getMessage());
      return new GenerateTestCasesRequest(
          List.of(), null, null, null, null, null, null, null, null);
    }
  }

  /** True when the request carries anything beyond bare requirement selection (the new flow). */
  private static boolean hasNewFlowData(GenerateTestCasesRequest req) {
    return req.hasFreeText()
        || !req.fileIdsOrEmpty().isEmpty()
        || !req.skillIdsOrEmpty().isEmpty()
        || req.systemPromptOverride() != null
        || req.userPromptOverride() != null
        || req.maxRounds() != null;
  }

  private static List<String> stringList(JsonNode node) {
    List<String> result = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(
          n -> {
            String val = n.asText(null);
            if (val != null && !val.isBlank()) result.add(val);
          });
    }
    return result;
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    String val = node.asText(null);
    return (val == null || val.isBlank()) ? null : val;
  }

  private String writeJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      return "[]";
    }
  }

  private static int clampRounds(Integer maxRounds) {
    if (maxRounds == null) return 3;
    return Math.max(1, Math.min(5, maxRounds));
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static UUID parseUuidOrNull(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return UUID.fromString(s.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Parse {@code {"answers":[{"id","answer"}]}} into resume-service answers. */
  private List<GenerationResumeService.Answer> parseAnswers(String body) {
    List<GenerationResumeService.Answer> out = new ArrayList<>();
    if (body == null || body.isBlank()) return out;
    try {
      JsonNode arr = mapper.readTree(body).path("answers");
      if (arr.isArray()) {
        for (JsonNode n : arr) {
          String id = n.path("id").asText(null);
          String answer = n.path("answer").asText(null);
          if (id != null) out.add(new GenerationResumeService.Answer(id, answer));
        }
      }
    } catch (Exception e) {
      log.debug("Could not parse answers body: {}", e.getMessage());
    }
    return out;
  }

  /** Read a top-level string field from an optional JSON body; null if absent/unparseable/blank. */
  private String jsonText(String body, String field) {
    if (body == null || body.isBlank()) return null;
    try {
      String v = mapper.readTree(body).path(field).asText(null);
      return v == null || v.isBlank() ? null : v;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Parses the optional request body for a "githubConfigId" UUID. Returns null if absent or
   * unparseable.
   */
  private UUID parseGithubConfigId(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      JsonNode root = mapper.readTree(body);
      String val = root.path("githubConfigId").asText(null);
      if (val != null && !val.isBlank()) {
        return UUID.fromString(val);
      }
    } catch (Exception e) {
      log.debug("Could not parse githubConfigId from body: {}", e.getMessage());
    }
    return null;
  }
}
