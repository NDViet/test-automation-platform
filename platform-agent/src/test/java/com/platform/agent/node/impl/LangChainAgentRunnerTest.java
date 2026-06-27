package com.platform.agent.node.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.contract.AgentGridFixtures;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.CheckpointService;
import com.platform.common.agent.AgentTaskType;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.LlmTier;
import com.platform.common.agent.NodeResult;
import com.platform.common.agent.NodeResultStatus;
import com.platform.common.agent.NodeType;
import com.platform.common.storage.BlobStore;
import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LangChainAgentRunnerTest {

  @Mock LlmChatModelProvider provider;
  @Mock LlmSettings settings;
  @Mock CheckpointService checkpointService;
  @Mock BlobStore blobStore;
  @Mock ChatModel chatModel;

  LangChainAgentRunner runner;

  /** Records tool dispatches so the test can assert the loop executed them. */
  static final class FakeNode implements AgentNode {
    int dispatchCount = 0;
    String lastTool;

    @Override
    public AgentTaskType taskType() {
      return AgentTaskType.values()[0];
    }

    @Override
    public NodeType nodeType() {
      return NodeType.values()[0];
    }

    @Override
    public NodeResult execute(ContextBundle bundle) {
      return null;
    }

    @Override
    public List<ToolSpecification> toolSpecs() {
      return List.of(ToolSpecification.builder().name("get_data").description("fetch").build());
    }

    @Override
    public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
      dispatchCount++;
      lastTool = toolName;
      return "DATA";
    }
  }

  @BeforeEach
  void setUp() {
    runner =
        new LangChainAgentRunner(
            provider, settings, checkpointService, blobStore, new ObjectMapper(), null);
    lenient().when(settings.model(anyString(), anyString())).thenReturn("claude-sonnet-4-6");
  }

  private static ChatResponse withTokens(AiMessage ai, int in, int out) {
    return ChatResponse.builder()
        .aiMessage(ai)
        .tokenUsage(new dev.langchain4j.model.output.TokenUsage(in, out))
        .build();
  }

  @Test
  void runsToolLoopThenReturnsCompletedWithAccumulatedTokens() {
    when(provider.chatModel("claude-sonnet-4-6")).thenReturn(chatModel);
    ChatResponse toolTurn =
        withTokens(
            AiMessage.from(
                ToolExecutionRequest.builder().id("1").name("get_data").arguments("{}").build()),
            10,
            5);
    ChatResponse finalTurn = withTokens(AiMessage.from("final answer"), 10, 5);
    when(chatModel.chat(any(ChatRequest.class))).thenReturn(toolTurn, finalTurn);

    FakeNode node = new FakeNode();
    NodeResult result = runner.run(AgentGridFixtures.bundle(), node);

    assertThat(result.status()).isEqualTo(NodeResultStatus.COMPLETED);
    assertThat(result.summary()).isEqualTo("final answer");
    assertThat(node.dispatchCount).isEqualTo(1);
    assertThat(node.lastTool).isEqualTo("get_data");
    // Two turns, each 10 input / 5 output.
    assertThat(result.tokenUsage().inputFresh()).isEqualTo(20);
    assertThat(result.tokenUsage().outputTokens()).isEqualTo(10);
  }

  @Test
  void failsWhenLiteLlmNotConfigured() {
    when(provider.chatModel(anyString())).thenReturn(null);

    NodeResult result = runner.run(AgentGridFixtures.bundle(), new FakeNode());

    assertThat(result.status()).isEqualTo(NodeResultStatus.FAILED);
    assertThat(result.errorCode()).isEqualTo("MISSING_LLM_CONFIG");
  }

  @Test
  void resolvesComplexTierToComplexModel() {
    when(settings.model(LangChainAgentRunner.KEY_MODEL_COMPLEX, "claude-opus-4-6"))
        .thenReturn("opus-route");
    assertThat(runner.resolveModelId(LlmTier.COMPLEX)).isEqualTo("opus-route");
  }
}
