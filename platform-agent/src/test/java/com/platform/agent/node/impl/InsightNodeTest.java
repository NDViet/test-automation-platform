package com.platform.agent.node.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.agent.contract.AgentGridFixtures;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.agent.node.tools.PlatformInsightTools;
import com.platform.common.agent.ContextBundle;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for InsightNode tool dispatch. Verifies tool routing, analytics client delegation, and
 * Slack skip-when-unconfigured.
 */
@ExtendWith(MockitoExtension.class)
class InsightNodeTest {

  @Mock private AgentOrchestrator orchestrator;
  @Mock private PlatformInsightTools insightTools;

  private InsightNode nodeWithSlack;
  private InsightNode nodeWithoutSlack;
  private ObjectMapper mapper;
  private ContextBundle bundle;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    bundle = AgentGridFixtures.bundle();
    // node with slack configured (uses a fake token — Slack POST will fail but we mock it)
    nodeWithSlack = new InsightNode(orchestrator, insightTools, mapper, "xoxb-fake-token");
    nodeWithoutSlack = new InsightNode(orchestrator, insightTools, mapper, "");
  }

  @Test
  void getTrends_delegatesToInsightTools() throws Exception {
    UUID projectId = AgentGridFixtures.PROJECT_ID;
    when(insightTools.getTrends(projectId, 7)).thenReturn("{\"passRate\":0.9}");

    String inputJson =
        mapper.writeValueAsString(Map.of("project_id", projectId.toString(), "days", 7));
    String result = nodeWithoutSlack.dispatchToolCall("platform_get_trends", inputJson, bundle);

    assertThat(result).contains("passRate");
    verify(insightTools).getTrends(projectId, 7);
  }

  @Test
  void getTrends_usesDefaultDays() throws Exception {
    UUID projectId = AgentGridFixtures.PROJECT_ID;
    when(insightTools.getTrends(eq(projectId), anyInt())).thenReturn("{\"passRate\":0.8}");

    String inputJson = mapper.writeValueAsString(Map.of("project_id", projectId.toString()));
    nodeWithoutSlack.dispatchToolCall("platform_get_trends", inputJson, bundle);

    verify(insightTools).getTrends(projectId, 7);
  }

  @Test
  void getFlakinessLeaderboard_delegatesToInsightTools() throws Exception {
    UUID projectId = AgentGridFixtures.PROJECT_ID;
    when(insightTools.getFlakinessLeaderboard(projectId, 10))
        .thenReturn("[{\"test\":\"FooTest\",\"score\":0.55}]");

    String inputJson =
        mapper.writeValueAsString(Map.of("project_id", projectId.toString(), "limit", 10));
    String result =
        nodeWithoutSlack.dispatchToolCall("platform_get_flakiness_leaderboard", inputJson, bundle);

    assertThat(result).contains("FooTest");
    verify(insightTools).getFlakinessLeaderboard(projectId, 10);
  }

  @Test
  void getQualityGate_delegatesToInsightTools() throws Exception {
    UUID projectId = AgentGridFixtures.PROJECT_ID;
    when(insightTools.getQualityGate(projectId)).thenReturn("{\"status\":\"PASS\"}");

    String inputJson = mapper.writeValueAsString(Map.of("project_id", projectId.toString()));
    String result =
        nodeWithoutSlack.dispatchToolCall("platform_get_quality_gate", inputJson, bundle);

    assertThat(result).contains("PASS");
    verify(insightTools).getQualityGate(projectId);
  }

  @Test
  void slackPostMessage_whenBotTokenBlank_skipsAndReturnsSafeMessage() throws Exception {
    String inputJson =
        mapper.writeValueAsString(
            Map.of(
                "channel", "#qa-digest",
                "text", "Nightly digest: 95% pass rate"));
    String result = nodeWithoutSlack.dispatchToolCall("slack_post_message", inputJson, bundle);

    assertThat(result).containsIgnoringCase("not configured").doesNotContain("Error");
    verifyNoInteractions(insightTools);
  }

  @Test
  void tools_declaresAllExpectedTools() {
    List<String> names = nodeWithoutSlack.toolSpecs().stream().map(t -> t.name()).toList();
    assertThat(names)
        .containsExactlyInAnyOrder(
            "platform_get_trends",
            "platform_get_flakiness_leaderboard",
            "platform_get_quality_gate",
            "slack_post_message");
  }

  @Test
  void unknownTool_returnsErrorMessage() {
    String result = nodeWithoutSlack.dispatchToolCall("does_not_exist", "{}", bundle);
    assertThat(result).contains("Unknown tool");
    verifyNoInteractions(insightTools);
  }

  @Test
  void nodeType_andTaskType_areCorrect() {
    assertThat(nodeWithoutSlack.nodeType().name()).isEqualTo("INSIGHT");
    assertThat(nodeWithoutSlack.taskType().name()).isEqualTo("GENERATE_NIGHTLY_DIGEST");
  }
}
