package com.platform.agent.node.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
  @Mock StreamingChatModel chatModel;

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
    // Generous idle limit so the streaming watchdog never fires during the synchronous test stubs.
    lenient().when(settings.timeoutSeconds()).thenReturn(180);
  }

  private static ChatResponse withTokens(AiMessage ai, int in, int out) {
    return ChatResponse.builder()
        .aiMessage(ai)
        .tokenUsage(new dev.langchain4j.model.output.TokenUsage(in, out))
        .build();
  }

  /**
   * Make the mocked streaming model deliver the given responses on successive calls, completing
   * each synchronously via {@code onCompleteResponse} (the last response repeats if called again).
   */
  private void stubStream(ChatResponse... responses) {
    AtomicInteger idx = new AtomicInteger(0);
    doAnswer(
            inv -> {
              StreamingChatResponseHandler handler = inv.getArgument(1);
              ChatResponse r = responses[Math.min(idx.getAndIncrement(), responses.length - 1)];
              handler.onCompleteResponse(r);
              return null;
            })
        .when(chatModel)
        .chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
  }

  @Test
  void runsToolLoopThenReturnsCompletedWithAccumulatedTokens() {
    when(provider.streamingChatModel("claude-sonnet-4-6")).thenReturn(chatModel);
    ChatResponse toolTurn =
        withTokens(
            AiMessage.from(
                ToolExecutionRequest.builder().id("1").name("get_data").arguments("{}").build()),
            10,
            5);
    ChatResponse finalTurn = withTokens(AiMessage.from("final answer"), 10, 5);
    stubStream(toolTurn, finalTurn);
    // Completion now checkpoints the conversation so it can be resumed for refinement.
    when(checkpointService.save(any(), any(), any())).thenReturn("chk-done");

    FakeNode node = new FakeNode();
    NodeResult result = runner.run(AgentGridFixtures.bundle(), node);

    assertThat(result.status()).isEqualTo(NodeResultStatus.COMPLETED);
    assertThat(result.summary()).isEqualTo("final answer");
    assertThat(result.checkpointId()).isEqualTo("chk-done"); // resumable at completion (C1)
    verify(checkpointService).save(any(), any(), any());
    assertThat(node.dispatchCount).isEqualTo(1);
    assertThat(node.lastTool).isEqualTo("get_data");
    // Two turns, each 10 input / 5 output.
    assertThat(result.tokenUsage().inputFresh()).isEqualTo(20);
    assertThat(result.tokenUsage().outputTokens()).isEqualTo(10);
  }

  @Test
  void askUserToolPausesWithAwaitingInputAndSavesCheckpoint() {
    when(provider.streamingChatModel("claude-sonnet-4-6")).thenReturn(chatModel);
    lenient().when(checkpointService.save(any(), any(), any())).thenReturn("chk-77");
    ChatResponse askTurn =
        withTokens(
            AiMessage.from(
                ToolExecutionRequest.builder()
                    .id("1")
                    .name("ask_user")
                    .arguments("{\"questions\":[{\"id\":\"q1\",\"question\":\"Which browsers?\"}]}")
                    .build()),
            10,
            5);
    stubStream(askTurn);

    AgentNode node =
        new AgentNode() {
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
            return List.of(ToolSpecification.builder().name("ask_user").description("ask").build());
          }

          @Override
          public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
            return "__AWAITING_INPUT__" + inputJson;
          }
        };

    NodeResult result = runner.run(AgentGridFixtures.bundle(), node);

    assertThat(result.status()).isEqualTo(NodeResultStatus.AWAITING_INPUT);
    assertThat(result.needsInput()).isTrue();
    assertThat(result.checkpointId()).isNotBlank(); // real round-trip wired in T14
    assertThat(result.summary()).contains("Which browsers?");
  }

  @Test
  void failsWhenLiteLlmNotConfigured() {
    when(provider.streamingChatModel(anyString())).thenReturn(null);

    NodeResult result = runner.run(AgentGridFixtures.bundle(), new FakeNode());

    assertThat(result.status()).isEqualTo(NodeResultStatus.FAILED);
    assertThat(result.errorCode()).isEqualTo("MISSING_LLM_CONFIG");
  }

  @Test
  void resumeLoadsCheckpointInjectsAnswersAndContinues() {
    // Persisted conversation: a system + user turn, serialized with LangChain4j's codec.
    List<dev.langchain4j.data.message.ChatMessage> saved =
        List.of(
            dev.langchain4j.data.message.SystemMessage.from("sys"),
            dev.langchain4j.data.message.UserMessage.from("original task"));
    String json = dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson(saved);

    // Round-trip sanity: the codec restores the same number of messages.
    assertThat(dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson(json))
        .hasSize(2);

    when(provider.streamingChatModel("claude-sonnet-4-6")).thenReturn(chatModel);
    com.platform.common.storage.BlobRef ref =
        new com.platform.common.storage.BlobRef(
            "platform-checkpoints", "k", "h", "application/json", 1);
    when(checkpointService.load("chk"))
        .thenReturn(
            java.util.Optional.of(
                new CheckpointService.ConversationState(
                    "session",
                    ref,
                    null,
                    null,
                    List.of(),
                    com.platform.common.agent.ResumeStrategy.COMPRESSED,
                    java.time.Instant.ofEpochSecond(1))));
    when(blobStore.fetchText(ref)).thenReturn(java.util.Optional.of(json));

    org.mockito.ArgumentCaptor<ChatRequest> reqCap =
        org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
    doAnswer(
            inv -> {
              StreamingChatResponseHandler handler = inv.getArgument(1);
              handler.onCompleteResponse(withTokens(AiMessage.from("done"), 3, 2));
              return null;
            })
        .when(chatModel)
        .chat(reqCap.capture(), any(StreamingChatResponseHandler.class));

    NodeResult result =
        runner.resume(AgentGridFixtures.bundle(), "chk", new FakeNode(), "ANSWER: chrome, firefox");

    assertThat(result.status()).isEqualTo(NodeResultStatus.COMPLETED);
    assertThat(result.summary()).isEqualTo("done");
    // The injected answers turn is present in the resumed conversation.
    String sent = reqCap.getValue().messages().toString();
    assertThat(sent).contains("ANSWER: chrome, firefox");
    assertThat(sent).contains("original task");
  }

  @Test
  void resumeFailsWhenCheckpointMissing() {
    when(provider.streamingChatModel("claude-sonnet-4-6")).thenReturn(chatModel);
    when(checkpointService.load("missing")).thenReturn(java.util.Optional.empty());

    NodeResult result =
        runner.resume(AgentGridFixtures.bundle(), "missing", new FakeNode(), "answer");

    assertThat(result.status()).isEqualTo(NodeResultStatus.FAILED);
  }

  @Test
  void modelOverrideBypassesTierModel() {
    // Only the override model is stubbed; if the runner fell back to the tier model the stream
    // model would be null → FAILED. COMPLETED proves node.modelOverride() was used.
    when(provider.streamingChatModel("custom-model")).thenReturn(chatModel);
    stubStream(withTokens(AiMessage.from("ok"), 1, 1));
    AgentNode node =
        new AgentNode() {
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
          public String modelOverride() {
            return "custom-model";
          }
        };

    NodeResult result = runner.run(AgentGridFixtures.bundle(), node);

    assertThat(result.status()).isEqualTo(NodeResultStatus.COMPLETED);
  }

  @Test
  void resolvesComplexTierToComplexModel() {
    when(settings.model(LangChainAgentRunner.KEY_MODEL_COMPLEX, "claude-opus-4-6"))
        .thenReturn("opus-route");
    assertThat(runner.resolveModelId(LlmTier.COMPLEX)).isEqualTo("opus-route");
  }
}
