package com.platform.agent.node.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepSummarizerImplTest {

  @Mock LlmChatModelProvider provider;
  @Mock LlmSettings settings;
  @Mock ChatModel chatModel;

  StepSummarizerImpl summarizer;

  @BeforeEach
  void setUp() {
    summarizer = new StepSummarizerImpl(provider, settings);
    lenient().when(settings.model(anyString(), anyString())).thenReturn("claude-haiku-4-5");
  }

  private static String big() {
    return "x".repeat(1000);
  }

  @Test
  void returnsShortResultUnchanged() {
    String short_ = "small result";
    assertThat(summarizer.summarize("tool", short_)).isEqualTo(short_);
  }

  @Test
  void summarizesLargeResultViaModel() {
    when(provider.chatModel("claude-haiku-4-5")).thenReturn(chatModel);
    when(chatModel.chat(any(ChatRequest.class)))
        .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("dense summary")).build());

    assertThat(summarizer.summarize("github_read_file", big())).isEqualTo("dense summary");
  }

  @Test
  void truncatesWhenLiteLlmNotConfigured() {
    when(provider.chatModel(anyString())).thenReturn(null);
    String out = summarizer.summarize("tool", big());
    assertThat(out).endsWith("[truncated]");
    assertThat(out.length()).isLessThan(1000);
  }
}
