package com.platform.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.classification.ClaudeAnalysisResult;
import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The single {@link AiClient} for failure classification — routed through the LiteLLM gateway via a
 * LangChain4j {@link ChatModel} (see {@code platform-llm}). The model id (configured under {@code
 * ai.litellm.model.analysis}) selects Claude/GPT/etc. inside LiteLLM.
 *
 * <p>Replaces the former provider-specific {@code ClaudeApiClient}/{@code OpenAiClient}/{@code
 * AiClientRouter}; marked {@link Primary} so it is injected everywhere {@link AiClient} is
 * autowired.
 */
@Primary
@Component
public class LiteLlmAnalysisClient implements AiClient {

  private static final Logger log = LoggerFactory.getLogger(LiteLlmAnalysisClient.class);

  private static final String KEY_MODEL_ANALYSIS = "ai.litellm.model.analysis";
  private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

  private final LlmChatModelProvider provider;
  private final LlmSettings settings;
  private final ObjectMapper objectMapper;

  public LiteLlmAnalysisClient(
      LlmChatModelProvider provider, LlmSettings settings, ObjectMapper objectMapper) {
    this.provider = provider;
    this.settings = settings;
    this.objectMapper = objectMapper;
  }

  @Override
  public AiAnalysisResponse analyse(String systemPrompt, String userPrompt) {
    String modelId = settings.model(KEY_MODEL_ANALYSIS, DEFAULT_MODEL);
    ChatModel model = provider.chatModel(modelId);
    if (model == null) {
      throw new IllegalStateException(
          "LiteLLM is not configured — set the base URL and API key in AI Settings");
    }

    // Let exceptions propagate — FailureClassificationService catches and persists an ERROR record
    // so the analysis can be retried once the gateway is reachable / configured.
    ChatResponse response =
        model.chat(
            ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .build());

    TokenUsage usage = response.tokenUsage();
    int inputTokens =
        usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
    int outputTokens =
        usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;

    String responseText = response.aiMessage() != null ? response.aiMessage().text() : "";
    log.debug("[AI] LiteLLM token usage: input={} output={}", inputTokens, outputTokens);
    return new AiAnalysisResponse(parseResponse(responseText), inputTokens, outputTokens);
  }

  @Override
  public String providerName() {
    return "litellm/" + settings.model(KEY_MODEL_ANALYSIS, DEFAULT_MODEL);
  }

  private ClaudeAnalysisResult parseResponse(String raw) {
    String json = raw == null ? "" : raw.trim();
    if (json.startsWith("```")) {
      int start = json.indexOf('{');
      int end = json.lastIndexOf('}');
      if (start >= 0 && end > start) {
        json = json.substring(start, end + 1);
      }
    }
    try {
      return objectMapper.readValue(json, ClaudeAnalysisResult.class);
    } catch (Exception e) {
      log.error("[AI] Failed to parse LiteLLM response: {}", e.getMessage());
      log.debug("[AI] Raw response: {}", raw);
      return unknownResult("JSON parse error: " + e.getMessage());
    }
  }

  private ClaudeAnalysisResult unknownResult(String reason) {
    return new ClaudeAnalysisResult(
        "UNKNOWN",
        0.0,
        reason,
        "Automated analysis unavailable",
        "Review the failure manually or retry after configuring LiteLLM",
        false,
        "Unknown");
  }
}
