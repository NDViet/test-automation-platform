package com.platform.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Builds (and caches) a LangChain4j {@link ChatModel} pointed at the configured LiteLLM endpoint.
 *
 * <p>This is the single seam through which the whole platform reaches an LLM: {@code platform-ai}
 * (failure analysis) and {@code platform-agent} (agentic workflows) both obtain their model here,
 * so the model id alone selects Claude/GPT/Gemini/etc. via LiteLLM's routing.
 *
 * <p>The model is rebuilt only when the resolved base URL / key / model id changes, so editing the
 * settings in the Portal takes effect without a restart.
 */
@Component
public class LlmChatModelProvider {

  private final LlmSettings settings;

  private volatile String cacheKey;
  private volatile ChatModel cached;

  public LlmChatModelProvider(LlmSettings settings) {
    this.settings = settings;
  }

  /**
   * Returns a chat model for {@code modelId}, or {@code null} when LiteLLM is not configured (no
   * base URL / key) or {@code modelId} is blank. Callers decide how to handle the unconfigured
   * case.
   */
  public synchronized ChatModel chatModel(String modelId) {
    if (!settings.isConfigured() || modelId == null || modelId.isBlank()) {
      return null;
    }
    String key = settings.baseUrl() + "|" + settings.apiKey() + "|" + modelId;
    if (!key.equals(cacheKey)) {
      cached =
          OpenAiChatModel.builder()
              .baseUrl(settings.baseUrl())
              .apiKey(settings.apiKey())
              .modelName(modelId)
              .timeout(Duration.ofSeconds(60))
              .build();
      cacheKey = key;
    }
    return cached;
  }
}
