package com.platform.agent.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.agent.hub.graph.GraphService;
import com.platform.agent.hub.graph.RequirementChangeProcessor;
import com.platform.agent.hub.graph.TestPlanGeneratorService;
import com.platform.agent.hub.graph.TokenBudgetGuard;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.agent.node.impl.InsightNode;
import com.platform.agent.node.impl.TestGenNode;
import com.platform.common.agent.*;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cross-component contract tests for the Agent Grid end-to-end flow.
 *
 * <p>Each nested class exercises one complete slice of the pipeline: Hub assembles context → Node
 * receives bundle → Tool dispatch → Side effects
 *
 * <p>These are unit-level tests (no Spring context, no DB) verifying that components speak the same
 * language across their boundaries.
 */
@ExtendWith(MockitoExtension.class)
class AgentGridContractTest {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  // -------------------------------------------------------------------------
  // Contract 1: AWAITING_REVIEW sentinel propagates through tool dispatch
  // -------------------------------------------------------------------------

  @Nested
  class AwaitingReviewSentinelContract {

    @Mock private AgentOrchestrator orchestrator;
    @Mock private PlatformTestCaseRepository testCaseRepo;
    @Mock private PlatformTraceabilityEdgeRepository edgeRepo;

    @Test
    void testGenNode_requestReview_returnsExactSentinelPrefix() throws Exception {
      TestGenNode node = new TestGenNode(orchestrator, testCaseRepo, edgeRepo, mapper);
      ContextBundle bundle = AgentGridFixtures.bundle();

      String payload = "{\"count\":2,\"cases\":[{\"title\":\"TC1\"}]}";
      String inputJson =
          mapper.writeValueAsString(
              Map.of("summary", "2 test cases ready for review", "payload", payload));

      String result = node.dispatchToolCall("request_review", inputJson, bundle);

      // ClaudeAgentOrchestrator checks startsWith("__AWAITING_REVIEW__")
      assertThat(result).startsWith("__AWAITING_REVIEW__");
      assertThat(result.substring("__AWAITING_REVIEW__".length()).strip()).isEqualTo(payload);
    }

    @Test
    void testGenNode_saveTestCases_doesNotReturnSentinel() throws Exception {
      TestGenNode node = new TestGenNode(orchestrator, testCaseRepo, edgeRepo, mapper);
      ContextBundle bundle = AgentGridFixtures.bundle();

      PlatformTestCase tc =
          new PlatformTestCase(AgentGridFixtures.PROJECT_ID, "TC", List.of(), "AGENT", null);
      when(testCaseRepo.save(any())).thenReturn(tc);

      String inputJson =
          mapper.writeValueAsString(
              Map.of(
                  "requirement_id", AgentGridFixtures.REQUIREMENT_ID.toString(),
                  "test_cases", List.of(Map.of("title", "TC1"))));

      String result = node.dispatchToolCall("save_test_cases", inputJson, bundle);
      assertThat(result).doesNotStartWith("__AWAITING_REVIEW__");
    }
  }

  // -------------------------------------------------------------------------
  // Contract 2: GraphService → RequirementContext → TestGenMode is consistent
  // -------------------------------------------------------------------------

  @Nested
  class GraphToContextContract {

    @Mock private PlatformRequirementRepository requirementRepo;
    @Mock private PlatformTestCaseRepository testCaseRepo;
    @Mock private PlatformTraceabilityEdgeRepository edgeRepo;
    @Mock private SotReleaseRepository releaseRepo;
    @Mock private ProjectIntegrationConfigRepository configRepo;

    @Test
    void freshRequirement_noTcs_modeIsCreateAll() {
      GraphService gs =
          new GraphService(requirementRepo, testCaseRepo, edgeRepo, releaseRepo, configRepo);
      UUID projectId = AgentGridFixtures.PROJECT_ID;
      UUID reqId = AgentGridFixtures.REQUIREMENT_ID;

      when(testCaseRepo.findCoveringTestCases(projectId, reqId)).thenReturn(List.of());
      when(edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, reqId, "REQUIREMENT"))
          .thenReturn(List.of());

      TestGenMode mode = gs.resolveTestGenMode(projectId, reqId);
      assertThat(mode).isEqualTo(TestGenMode.CREATE_ALL);
    }

    @Test
    void allTcsActive_modeIsNoAction() {
      GraphService gs =
          new GraphService(requirementRepo, testCaseRepo, edgeRepo, releaseRepo, configRepo);
      UUID projectId = AgentGridFixtures.PROJECT_ID;
      UUID reqId = AgentGridFixtures.REQUIREMENT_ID;

      PlatformTestCase activeTc = new PlatformTestCase(projectId, "TC", List.of(), "AGENT", null);
      setField(activeTc, "id", UUID.randomUUID());
      when(testCaseRepo.findCoveringTestCases(projectId, reqId)).thenReturn(List.of(activeTc));

      TestGenMode mode = gs.resolveTestGenMode(projectId, reqId);
      assertThat(mode).isEqualTo(TestGenMode.NO_ACTION);
    }
  }

  // -------------------------------------------------------------------------
  // Contract 3: RequirementChangeProcessor → marks TCs → TestGenMode changes
  // -------------------------------------------------------------------------

  @Nested
  class ChangeProcessorToGraphContract {

    @Mock private PlatformRequirementRepository requirementRepo;
    @Mock private PlatformTestCaseRepository testCaseRepo;
    @Mock private SotReleaseRepository releaseRepo;
    @Mock private GraphService graphService;
    @Mock private PlatformTraceabilityEdgeRepository edgeRepo;
    @Mock private SotReleaseRepository releaseRepo2;
    @Mock private ProjectIntegrationConfigRepository configRepo;

    @Test
    void changedRequirement_tcMarkedNeedsUpdate_graphReturnsUpdateChangedMode() {
      UUID projectId = AgentGridFixtures.PROJECT_ID;
      UUID reqId = AgentGridFixtures.REQUIREMENT_ID;

      // Step 1: simulate change processor marking a TC as NEEDS_UPDATE
      UUID tcId = UUID.randomUUID();
      PlatformTestCase tc =
          new PlatformTestCase(projectId, "Old TC", List.of("AC-1"), "AGENT", null);
      setField(tc, "id", tcId);
      // tc starts as ACTIVE → processor will mark it NEEDS_UPDATE
      assertThat(tc.getCoverageStatus()).isEqualTo("ACTIVE");

      PlatformRequirement req = makeReqWithHash(projectId, reqId, "Old", "Old");
      when(requirementRepo.findById(reqId)).thenReturn(Optional.of(req));
      when(graphService.getTestCases(projectId, reqId)).thenReturn(List.of(tc));
      when(testCaseRepo.findById(tcId)).thenReturn(Optional.of(tc));
      when(testCaseRepo.save(any())).thenAnswer(i -> i.getArgument(0));
      when(graphService.getReleasesFor(projectId, reqId)).thenReturn(List.of());

      RequirementChangeProcessor processor =
          new RequirementChangeProcessor(requirementRepo, testCaseRepo, releaseRepo, graphService);
      var impact = processor.computeImpact(projectId, reqId, "New Title", "New Desc");

      assertThat(impact).isNotNull();
      assertThat(tc.getCoverageStatus()).isEqualTo("NEEDS_UPDATE");

      // Step 2: now GraphService should return UPDATE_CHANGED mode
      GraphService gs =
          new GraphService(requirementRepo, testCaseRepo, edgeRepo, releaseRepo2, configRepo);
      when(testCaseRepo.findCoveringTestCases(projectId, reqId)).thenReturn(List.of(tc));

      TestGenMode mode = gs.resolveTestGenMode(projectId, reqId);
      assertThat(mode).isEqualTo(TestGenMode.UPDATE_CHANGED);
    }
  }

  // -------------------------------------------------------------------------
  // Contract 4: TokenBudgetGuard integrates with workflow budget check
  // -------------------------------------------------------------------------

  @Nested
  class BudgetGuardContract {

    @Mock private AgentTokenBudgetRepository budgetRepo;

    @Test
    void hardLimitExceeded_tokenUsageRecordedCorrectly() {
      TokenBudgetGuard guard = new TokenBudgetGuard(budgetRepo);
      UUID projectId = AgentGridFixtures.PROJECT_ID;
      String month = YearMonth.now().toString();

      // Exceed hard limit
      AgentTokenBudget budget = new AgentTokenBudget(projectId, month);
      setField(budget, "hardLimit", true);
      setField(budget, "usedCostCents", new BigDecimal("5001.00"));
      when(budgetRepo.findByProjectIdAndBudgetMonth(projectId, month))
          .thenReturn(Optional.of(budget));

      assertThat(guard.isWithinBudget(projectId)).isFalse();

      // After guard rejects, workflow would not execute, so recordUsage is not called
      verify(budgetRepo, never()).upsertUsage(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void tokenUsage_fromCompletedNode_recordedAtomically() {
      TokenBudgetGuard guard = new TokenBudgetGuard(budgetRepo);
      UUID projectId = AgentGridFixtures.PROJECT_ID;
      String month = YearMonth.now().toString();

      TokenUsage usage = new TokenUsage(2000, 500, 1000, 400, BigDecimal.valueOf(1.20));
      doNothing().when(budgetRepo).upsertUsage(any(), any(), anyLong(), anyLong(), any());
      when(budgetRepo.findByProjectIdAndBudgetMonth(any(), any())).thenReturn(Optional.empty());

      guard.recordUsage(projectId, usage);

      // totalInputTokens = 2000 + 500 + 1000 = 3500
      verify(budgetRepo).upsertUsage(projectId, month, 3500L, 400L, BigDecimal.valueOf(1.20));
    }
  }

  // -------------------------------------------------------------------------
  // Contract 5: TestPlanGeneratorService → coverage score ∈ [0, 1]
  // -------------------------------------------------------------------------

  @Nested
  class TestPlanCoverageContract {

    @Mock private SotReleaseRepository releaseRepo;
    @Mock private SotTestPlanRepository planRepo;
    @Mock private SotTestPlanItemRepository itemRepo;
    @Mock private SotReleaseRequirementRepository releaseReqRepo;
    @Mock private PlatformTestCaseRepository testCaseRepo;
    @Mock private GraphService graphService;

    @Test
    void coverageScore_isAlwaysBetweenZeroAndOne() {
      UUID projectId = AgentGridFixtures.PROJECT_ID;
      UUID releaseId = UUID.randomUUID();
      UUID req1 = UUID.randomUUID();
      UUID tc1 = UUID.randomUUID();

      SotRelease release = new SotRelease(projectId, "v3.0", "VERSION", null);
      setField(release, "id", releaseId);
      when(releaseRepo.findById(releaseId)).thenReturn(Optional.of(release));

      SotTestPlan plan = new SotTestPlan(projectId, releaseId);
      setField(plan, "id", UUID.randomUUID());
      when(planRepo.findByProjectIdAndReleaseId(any(), any())).thenReturn(Optional.empty());
      when(planRepo.save(any(SotTestPlan.class))).thenReturn(plan);
      when(itemRepo.findByPlanId(any())).thenReturn(List.of());
      when(releaseReqRepo.findRequirementIdsByReleaseId(releaseId)).thenReturn(List.of(req1));

      PlatformTestCase tc = new PlatformTestCase(projectId, "TC", List.of(), "AGENT", null);
      setField(tc, "id", tc1);
      when(graphService.getTestCases(projectId, req1)).thenReturn(List.of(tc));
      when(testCaseRepo.findById(tc1)).thenReturn(Optional.of(tc));
      when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

      TestPlanGeneratorService svc =
          new TestPlanGeneratorService(
              releaseRepo, planRepo, itemRepo, releaseReqRepo, testCaseRepo, graphService);
      SotTestPlan result = svc.generateForRelease(projectId, releaseId);

      assertThat(result.getCoverageScore().doubleValue()).isBetween(0.0, 1.0);
    }
  }

  // -------------------------------------------------------------------------
  // Contract 6: InsightNode → analytics service unavailable → graceful fallback
  // -------------------------------------------------------------------------

  @Nested
  class InsightFallbackContract {

    @Mock private AgentOrchestrator orchestrator;
    @Mock private com.platform.agent.node.tools.PlatformInsightTools insightTools;

    @Test
    void getTrends_serviceReturnsError_nodeReturnsFallbackJson() throws Exception {
      when(insightTools.getTrends(any(), anyInt()))
          .thenReturn("{\"error\":\"analytics service unavailable\"}");

      InsightNode node = new InsightNode(orchestrator, insightTools, mapper, "");
      ContextBundle bundle = AgentGridFixtures.bundle();

      String input =
          mapper.writeValueAsString(Map.of("project_id", AgentGridFixtures.PROJECT_ID.toString()));
      String result = node.dispatchToolCall("platform_get_trends", input, bundle);

      assertThat(result).contains("error");
      // Crucially: does NOT throw, does NOT return "__AWAITING_REVIEW__"
      assertThat(result).doesNotStartWith("__AWAITING_REVIEW__");
    }
  }

  // -------------------------------------------------------------------------
  // Contract 7: Every AgentNode declares consistent nodeType/taskType
  // -------------------------------------------------------------------------

  @Nested
  class NodeTypeConsistencyContract {

    @Test
    void testGenNode_nodeTypeTaskTypeAreConsistent() {
      TestGenNode node = new TestGenNode(null, null, null, mapper);
      assertThat(node.nodeType()).isEqualTo(NodeType.TEST_GEN);
      assertThat(node.taskType()).isEqualTo(AgentTaskType.GENERATE_AUTOMATED_TESTS);
    }

    @Test
    void insightNode_nodeTypeTaskTypeAreConsistent() {
      InsightNode node = new InsightNode(null, null, mapper, "");
      assertThat(node.nodeType()).isEqualTo(NodeType.INSIGHT);
      assertThat(node.taskType()).isEqualTo(AgentTaskType.GENERATE_NIGHTLY_DIGEST);
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private PlatformRequirement makeReqWithHash(
      UUID projectId, UUID id, String title, String description) {
    PlatformRequirement req =
        new PlatformRequirement(projectId, null, "EXT-1", title, description, "STORY");
    setField(req, "id", id);
    String hash = sha256(title + "\0" + description);
    setField(req, "versionHash", hash);
    return req;
  }

  private String sha256(String input) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void setField(Object obj, String fieldName, Object value) {
    try {
      java.lang.reflect.Field f = findField(obj.getClass(), fieldName);
      f.setAccessible(true);
      f.set(obj, value);
    } catch (Exception e) {
      throw new RuntimeException("Cannot set field " + fieldName, e);
    }
  }

  private java.lang.reflect.Field findField(Class<?> clazz, String name) {
    Class<?> c = clazz;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        c = c.getSuperclass();
      }
    }
    throw new RuntimeException("Field not found: " + name);
  }
}
