package com.platform.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LiteLlmAnalysisClientTest {

  @Mock LlmChatModelProvider provider;
  @Mock LlmSettings settings;
  @Mock ChatModel chatModel;

  LiteLlmAnalysisClient client;

  @BeforeEach
  void setUp() {
    client = new LiteLlmAnalysisClient(provider, settings, new ObjectMapper());
  }

  private ChatResponse responseOf(String text, int in, int out) {
    return ChatResponse.builder()
        .aiMessage(AiMessage.from(text))
        .tokenUsage(new TokenUsage(in, out))
        .build();
  }

  @Test
  void parsesClassificationAndTokenUsage() {
    when(settings.model(anyString(), anyString())).thenReturn("claude-sonnet-4-6");
    when(provider.chatModel("claude-sonnet-4-6")).thenReturn(chatModel);
    String json =
        "{\"category\":\"PRODUCT_BUG\",\"confidence\":0.9,\"rootCause\":\"NPE\","
            + "\"detailedAnalysis\":\"x\",\"suggestedFix\":\"fix\",\"isFlakyCandidate\":false,"
            + "\"affectedComponent\":\"checkout\"}";
    when(chatModel.chat(any(ChatRequest.class))).thenReturn(responseOf(json, 12, 34));

    AiAnalysisResponse r = client.analyse("system", "user");

    assertThat(r.result().category()).isEqualTo("PRODUCT_BUG");
    assertThat(r.result().confidence()).isEqualTo(0.9);
    assertThat(r.result().affectedComponent()).isEqualTo("checkout");
    assertThat(r.inputTokens()).isEqualTo(12);
    assertThat(r.outputTokens()).isEqualTo(34);
  }

  @Test
  void stripsMarkdownFencesBeforeParsing() {
    when(settings.model(anyString(), anyString())).thenReturn("m");
    when(provider.chatModel("m")).thenReturn(chatModel);
    String fenced = "```json\n{\"category\":\"FLAKY\",\"confidence\":0.5}\n```";
    when(chatModel.chat(any(ChatRequest.class))).thenReturn(responseOf(fenced, 1, 2));

    AiAnalysisResponse r = client.analyse("s", "u");

    assertThat(r.result().category()).isEqualTo("FLAKY");
  }

  @Test
  void returnsUnknownOnUnparseableResponse() {
    when(settings.model(anyString(), anyString())).thenReturn("m");
    when(provider.chatModel("m")).thenReturn(chatModel);
    when(chatModel.chat(any(ChatRequest.class))).thenReturn(responseOf("not json", 1, 2));

    AiAnalysisResponse r = client.analyse("s", "u");

    assertThat(r.result().category()).isEqualTo("UNKNOWN");
  }

  @Test
  void throwsWhenLiteLlmNotConfigured() {
    when(settings.model(anyString(), anyString())).thenReturn("m");
    when(provider.chatModel("m")).thenReturn(null);

    assertThatThrownBy(() -> client.analyse("s", "u")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void providerNameIncludesModel() {
    when(settings.model(anyString(), anyString())).thenReturn("gpt-4o");
    assertThat(client.providerName()).isEqualTo("litellm/gpt-4o");
  }
}
