package com.platform.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.common.agent.*;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Triggers a PROPOSE_HEAL_FIX workflow for a specific flaky test. Enriches the ContextBundle with
 * targeted failure samples + AI analysis context before routing to HealingNode.
 *
 * <p>POST /hub/healing/{projectId}/trigger Body: { "testId": "...", "flakyScoreId": "uuid",
 * "githubConfigId": "uuid" }
 */
@RestController
@RequestMapping("/hub/healing")
public class FlakyTestFixController {

  private static final Logger log = LoggerFactory.getLogger(FlakyTestFixController.class);
  private static final int SAMPLE_LIMIT = 10;

  private final AgentWorkflowService workflowService;
  private final ContextAssembler contextAssembler;
  private final ObjectMapper mapper;
  private final ProjectRepository projectRepo;
  private final FlakinessScoreRepository flakinessRepo;
  private final FailureAnalysisRepository analysisRepo;
  private final TestCaseResultRepository resultRepo;
  private final ProjectIntegrationConfigRepository integrationRepo;

  public FlakyTestFixController(
      AgentWorkflowService workflowService,
      ContextAssembler contextAssembler,
      ObjectMapper mapper,
      ProjectRepository projectRepo,
      FlakinessScoreRepository flakinessRepo,
      FailureAnalysisRepository analysisRepo,
      TestCaseResultRepository resultRepo,
      ProjectIntegrationConfigRepository integrationRepo) {
    this.workflowService = workflowService;
    this.contextAssembler = contextAssembler;
    this.mapper = mapper;
    this.projectRepo = projectRepo;
    this.flakinessRepo = flakinessRepo;
    this.analysisRepo = analysisRepo;
    this.resultRepo = resultRepo;
    this.integrationRepo = integrationRepo;
  }

  @PostMapping("/{projectId}/trigger")
  @Transactional(readOnly = true)
  public ResponseEntity<Map<String, Object>> triggerFix(
      @PathVariable UUID projectId, @RequestBody(required = false) String body) {

    String testId = null;
    String githubConfigId = null;

    if (body != null && !body.isBlank()) {
      try {
        JsonNode root = mapper.readTree(body);
        testId = root.path("testId").asText(null);
        githubConfigId = root.path("githubConfigId").asText(null);
      } catch (Exception e) {
        log.debug("Could not parse request body: {}", e.getMessage());
      }
    }

    if (testId == null || testId.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "testId is required"));
    }

    final String resolvedTestId = testId;
    final String resolvedConfigId = githubConfigId;

    projectRepo
        .findById(projectId)
        .orElseThrow(() -> new IllegalArgumentException("project not found: " + projectId));

    TriggerRef trigger =
        new TriggerRef(
            TriggerRef.TriggerType.MANUAL,
            null,
            "flaky_fix",
            resolvedTestId,
            null,
            null,
            Instant.now());

    try {
      AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
      ContextBundle base = contextAssembler.assemble(workflow.getId(), projectId, trigger);

      // Enrich ExecutionContext with targeted failure samples for this specific test
      ContextBundle enriched =
          enrichWithFlakyContext(base, projectId, resolvedTestId, resolvedConfigId);

      workflowService.executeWorkflow(workflow.getId(), enriched);

      log.info(
          "Flaky fix workflow {} started for test '{}' in project {}",
          workflow.getId(),
          resolvedTestId,
          projectId);

      Map<String, Object> resp = new LinkedHashMap<>();
      resp.put("workflowId", workflow.getId().toString());
      resp.put("projectId", projectId.toString());
      resp.put("testId", resolvedTestId);
      resp.put("message", "Fix generation started — a draft PR will be raised to the target repo.");
      return ResponseEntity.accepted().body(resp);

    } catch (Exception e) {
      log.error(
          "Failed to start flaky fix for test '{}' in project {}: {}",
          resolvedTestId,
          projectId,
          e.getMessage(),
          e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Failed to start fix workflow: " + e.getMessage()));
    }
  }

  private ContextBundle enrichWithFlakyContext(
      ContextBundle base, UUID projectId, String testId, String githubConfigId) {
    Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);

    // Load targeted failure samples for this test
    List<TestCaseResult> failures =
        resultRepo.findByTestIdAndProjectIdSince(testId, projectId, since30d);

    List<ExecutionContext.FailureSample> samples =
        failures.stream()
            .limit(SAMPLE_LIMIT)
            .map(
                r ->
                    new ExecutionContext.FailureSample(
                        r.getTestId(),
                        r.getDisplayName(),
                        r.getFailureMessage(),
                        r.getStackTrace(),
                        r.getCreatedAt()))
            .collect(Collectors.toList());

    // Append AI analysis context as a synthetic "sample" if available
    analysisRepo
        .findTopByTestIdAndProjectIdOrderByAnalysedAtDesc(testId, projectId)
        .ifPresent(
            analysis -> {
              String aiContext = buildAnalysisContext(analysis);
              samples.add(
                  new ExecutionContext.FailureSample(
                      testId,
                      "[AI Analysis] " + testId,
                      aiContext,
                      null,
                      analysis.getAnalysedAt()));
            });

    // Load flakiness score for context
    FlakinessScore flakinessScore =
        flakinessRepo.findByTestIdAndProjectId(testId, projectId).orElse(null);

    // Resolve GitHub credentials from the selected config
    SessionCredentials credentials = resolveCredentials(projectId, githubConfigId);

    // Build an enriched ExecutionContext focused on this specific test
    ExecutionContext enrichedCtx =
        new ExecutionContext(
            base.executionContext() != null ? base.executionContext().lastRunId() : null,
            base.executionContext() != null ? base.executionContext().lastRunAt() : null,
            base.executionContext() != null ? base.executionContext().passRate7d() : 0.0,
            flakinessScore != null ? flakinessScore.getScore().doubleValue() : 0.0,
            base.executionContext() != null ? base.executionContext().consecutiveFailures() : 0,
            samples,
            testId);

    return new ContextBundle(
        base.sessionId(),
        base.workflowId(),
        base.projectId(),
        base.projectSlug(),
        List.of(AgentTaskType.PROPOSE_HEAL_FIX),
        base.trigger(),
        base.requirementContext(),
        base.testCaseContext(),
        base.automatedTestContext(),
        enrichedCtx,
        base.monitorContext(),
        credentials,
        base.outboundTargets(),
        base.prDiff(),
        base.releaseVersion(),
        base.resumeStrategy(),
        base.checkpointId(),
        LlmTier.STANDARD,
        Instant.now());
  }

  private String buildAnalysisContext(FailureAnalysis analysis) {
    return String.format(
        "AI Analysis Result (confidence=%.0f%%):\n"
            + "Category: %s\n"
            + "Flaky Candidate: %s\n"
            + "Root Cause: %s\n"
            + "Detailed Analysis: %s\n"
            + "Suggested Fix: %s",
        analysis.getConfidence() * 100,
        analysis.getCategory(),
        analysis.isFlakyCandidate() ? "YES" : "NO",
        analysis.getRootCause(),
        analysis.getDetailedAnalysis(),
        analysis.getSuggestedFix());
  }

  private SessionCredentials resolveCredentials(UUID projectId, String githubConfigId) {
    // Find GitHub config — prefer the specified one, fall back to any GITHUB config
    List<ProjectIntegrationConfig> configs = integrationRepo.findByProjectId(projectId);
    ProjectIntegrationConfig config = null;

    if (githubConfigId != null && !githubConfigId.isBlank()) {
      UUID configUuid = UUID.fromString(githubConfigId);
      config = configs.stream().filter(c -> c.getId().equals(configUuid)).findFirst().orElse(null);
    }
    if (config == null) {
      config =
          configs.stream()
              .filter(c -> "GITHUB".equals(c.getIntegrationType()) && c.isEnabled())
              .findFirst()
              .orElse(null);
    }

    if (config == null) return null;

    String token = config.param("token");
    if (token == null || token.isBlank()) return null;

    return new SessionCredentials(
        Map.of(IntegrationType.GITHUB.name(), token), Instant.now().plus(1, ChronoUnit.HOURS));
  }
}
