package com.platform.agent.hub.impl;

import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.hub.graph.GraphService;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.common.agent.*;
import com.platform.common.enums.TestStatus;
import com.platform.common.integration.IntegrationType;
import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.common.storage.BlobStoreBuckets;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Builds a ContextBundle from live platform-core data. Populates execution tier (pass rate,
 * flakiness) and leaves requirement/test-case tiers null until requirement-tracking entities are
 * persisted in a later phase.
 */
@Component
public class DefaultContextAssembler implements ContextAssembler {

  private static final Logger log = LoggerFactory.getLogger(DefaultContextAssembler.class);
  private static final int RECENT_FAILURE_LIMIT = 10;
  private static final Pattern GITHUB_PR_URL =
      Pattern.compile("https://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");

  private final ProjectRepository projectRepo;
  private final TestExecutionRepository executionRepo;
  private final TestCaseResultRepository testCaseResultRepo;
  private final FlakinessScoreRepository flakinessRepo;
  private final AgentWorkflowRepository workflowRepo;
  private final PlatformRequirementRepository requirementRepo;
  private final BlobStore blobStore;
  private final GraphService graphService;

  private GitHubApiClient gitHubApiClient;

  public DefaultContextAssembler(
      ProjectRepository projectRepo,
      TestExecutionRepository executionRepo,
      TestCaseResultRepository testCaseResultRepo,
      FlakinessScoreRepository flakinessRepo,
      AgentWorkflowRepository workflowRepo,
      PlatformRequirementRepository requirementRepo,
      BlobStore blobStore,
      GraphService graphService) {
    this.projectRepo = projectRepo;
    this.executionRepo = executionRepo;
    this.testCaseResultRepo = testCaseResultRepo;
    this.flakinessRepo = flakinessRepo;
    this.workflowRepo = workflowRepo;
    this.requirementRepo = requirementRepo;
    this.blobStore = blobStore;
    this.graphService = graphService;
  }

  @Autowired(required = false)
  public void setGitHubApiClient(GitHubApiClient gitHubApiClient) {
    this.gitHubApiClient = gitHubApiClient;
  }

  @Override
  public ContextBundle assemble(UUID workflowId, UUID projectId, TriggerRef trigger) {
    Project project =
        projectRepo
            .findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found: " + projectId));

    ExecutionContext execCtx = buildExecutionContext(projectId);
    BlobRef prDiffRef = prefetchPrDiff(trigger);
    RequirementContext reqCtx = buildRequirementContext(projectId, trigger);

    return new ContextBundle(
        UUID.randomUUID(), // sessionId — unique per assembly
        workflowId,
        projectId,
        project.getSlug(),
        inferTaskTypes(trigger),
        trigger,
        reqCtx,
        null, // testCaseContext
        null, // automatedTestContext
        execCtx,
        null, // monitorContext
        null, // credentials — injected by secrets manager (future)
        null, // outboundTargets
        prDiffRef,
        null, // releaseVersion
        ResumeStrategy.COMPRESSED,
        null, // checkpointId — new session
        LlmTier.STANDARD,
        Instant.now());
  }

  @Override
  public ContextBundle resume(UUID workflowId, String checkpointId) {
    AgentWorkflow workflow =
        workflowRepo
            .findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("workflow not found: " + workflowId));

    Project project =
        projectRepo
            .findById(workflow.getProjectId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException("project not found: " + workflow.getProjectId()));

    ExecutionContext execCtx = buildExecutionContext(workflow.getProjectId());

    // Infer trigger type from stored workflow data
    TriggerRef trigger =
        new TriggerRef(
            TriggerRef.TriggerType.valueOf(workflow.getTriggerType()),
            null,
            null,
            null,
            null,
            null,
            workflow.getCreatedAt());

    return new ContextBundle(
        UUID.randomUUID(),
        workflowId,
        workflow.getProjectId(),
        project.getSlug(),
        inferTaskTypes(trigger),
        trigger,
        null,
        null,
        null,
        execCtx,
        null,
        null,
        null,
        null,
        null,
        ResumeStrategy.COMPRESSED,
        checkpointId,
        LlmTier.STANDARD,
        Instant.now());
  }

  // -------------------------------------------------------------------------

  private ExecutionContext buildExecutionContext(UUID projectId) {
    Instant since7d = Instant.now().minus(7, ChronoUnit.DAYS);

    Double passRate = executionRepo.computePassRate(projectId, since7d);
    double pass7d = passRate != null ? passRate : 0.0;

    List<TestExecution> recent =
        executionRepo
            .findByProjectIdOrderByExecutedAtDesc(projectId, PageRequest.of(0, 5))
            .getContent();
    int consecutiveFails = countConsecutiveFailures(recent);

    TestExecution lastRun = recent.isEmpty() ? null : recent.get(0);
    UUID lastRunId = lastRun != null ? lastRun.getId() : null;
    Instant lastRunAt = lastRun != null ? lastRun.getExecutedAt() : null;

    double flakinessAvg = computeProjectFlakinessAvg(projectId);
    List<ExecutionContext.FailureSample> failures = buildFailureSamples(projectId, since7d);

    return new ExecutionContext(
        lastRunId, lastRunAt, pass7d, flakinessAvg, consecutiveFails, failures, "default");
  }

  private int countConsecutiveFailures(List<TestExecution> executions) {
    int count = 0;
    for (TestExecution e : executions) {
      int total = e.getTotalTests();
      int passed = e.getPassed();
      if (total > 0 && passed < total) {
        count++;
      } else {
        break;
      }
    }
    return count;
  }

  private double computeProjectFlakinessAvg(UUID projectId) {
    List<FlakinessScore> scores =
        flakinessRepo.findTopFlakyByProject(projectId, PageRequest.of(0, 50));
    if (scores.isEmpty()) return 0.0;
    return scores.stream().mapToDouble(s -> s.getScore().doubleValue()).average().orElse(0.0);
  }

  private List<ExecutionContext.FailureSample> buildFailureSamples(UUID projectId, Instant since) {
    List<TestExecution> executions =
        executionRepo.findByProjectAndDateRange(projectId, since, Instant.now());

    return executions.stream()
        .flatMap(
            exec ->
                testCaseResultRepo
                    .findByExecutionIdAndStatus(exec.getId(), TestStatus.FAILED)
                    .stream()
                    .limit(3))
        .limit(RECENT_FAILURE_LIMIT)
        .map(
            r ->
                new ExecutionContext.FailureSample(
                    r.getTestId(),
                    r.getDisplayName(),
                    r.getFailureMessage(),
                    r.getStackTrace(),
                    r.getCreatedAt()))
        .collect(Collectors.toList());
  }

  /**
   * When the trigger carries a JIRA/Linear entity external ID, looks up the matching
   * PlatformRequirement and delegates to GraphService for full context. Returns null when no
   * requirement is available (e.g. pure PR triggers).
   */
  private RequirementContext buildRequirementContext(UUID projectId, TriggerRef trigger) {
    if (trigger == null || trigger.entityExternalId() == null) return null;
    try {
      return requirementRepo
          .findByProjectIdAndExternalId(projectId, trigger.entityExternalId())
          .map(req -> graphService.buildRequirementContext(projectId, req.getId()))
          .orElse(null);
    } catch (Exception e) {
      log.warn(
          "Failed to build requirement context for trigger entity {}: {}",
          trigger.entityExternalId(),
          e.getMessage());
      return null;
    }
  }

  /**
   * For GitHub webhook triggers, fetches changed files from the GitHub API and stores the result in
   * the DIFFS bucket. Returns null if not applicable or fetch fails. The blob is referenced in
   * ContextBundle so AnalysisNode can read it without an extra tool call.
   */
  private BlobRef prefetchPrDiff(TriggerRef trigger) {
    if (trigger == null
        || trigger.triggerType() != TriggerRef.TriggerType.WEBHOOK
        || trigger.source() != IntegrationType.GITHUB
        || trigger.refUrl() == null
        || gitHubApiClient == null) {
      return null;
    }
    Matcher m = GITHUB_PR_URL.matcher(trigger.refUrl());
    if (!m.matches()) {
      log.debug("Could not parse PR URL for diff pre-fetch: {}", trigger.refUrl());
      return null;
    }
    String owner = m.group(1);
    String repo = m.group(2);
    int prNumber = Integer.parseInt(m.group(3));
    try {
      String filesJson = gitHubApiClient.getPrFiles(owner, repo, prNumber, "");
      BlobRef ref = blobStore.storeText(BlobStoreBuckets.DIFFS, filesJson, BlobRef.TYPE_JSON);
      log.info("Pre-fetched PR diff for {}/{}#{} → blob {}", owner, repo, prNumber, ref.key());
      return ref;
    } catch (Exception e) {
      log.warn(
          "Failed to pre-fetch PR diff for {}/{}#{}: {}", owner, repo, prNumber, e.getMessage());
      return null;
    }
  }

  private List<AgentTaskType> inferTaskTypes(TriggerRef trigger) {
    if (trigger == null) return List.of(AgentTaskType.GENERATE_MANUAL_TEST_CASES);
    return switch (trigger.triggerType()) {
      case WEBHOOK -> List.of(AgentTaskType.ANALYZE_PR_DIFF, AgentTaskType.DETECT_COVERAGE_GAPS);
      case SCHEDULE ->
          List.of(AgentTaskType.GENERATE_NIGHTLY_DIGEST, AgentTaskType.INVESTIGATE_FLAKINESS);
      case MANUAL -> inferManualTasks(trigger);
      case API_CALL -> List.of(AgentTaskType.GENERATE_AUTOMATED_TESTS);
    };
  }

  /**
   * A MANUAL trigger fires several distinct flows; the controller records which one in {@code
   * entityType} (e.g. {@code generate_test_cases}, {@code generate_automation_code}). Routing must
   * honour that so the request reaches the node actually registered for it — otherwise the workflow
   * runs unrelated tasks (e.g. acceptance-criteria extraction) and never the requested generation.
   */
  private List<AgentTaskType> inferManualTasks(TriggerRef trigger) {
    String entityType = trigger.entityType();
    if ("generate_test_cases".equals(entityType)) {
      return List.of(AgentTaskType.GENERATE_TEST_CASES);
    }
    if ("generate_automation_code".equals(entityType)) {
      return List.of(AgentTaskType.GENERATE_AUTOMATION_CODE);
    }
    // Legacy/generic manual trigger.
    return List.of(
        AgentTaskType.EXTRACT_ACCEPTANCE_CRITERIA, AgentTaskType.GENERATE_MANUAL_TEST_CASES);
  }
}
