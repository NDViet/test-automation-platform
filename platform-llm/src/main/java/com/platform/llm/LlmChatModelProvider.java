package com.platform.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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

  private volatile String streamCacheKey;
  private volatile StreamingChatModel cachedStream;

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
    int timeoutSeconds = settings.timeoutSeconds();
    String key =
        settings.baseUrl() + "|" + settings.apiKey() + "|" + modelId + "|" + timeoutSeconds;
    if (!key.equals(cacheKey)) {
      cached =
          OpenAiChatModel.builder()
              .baseUrl(settings.baseUrl())
              .apiKey(settings.apiKey())
              .modelName(modelId)
              .timeout(Duration.ofSeconds(timeoutSeconds))
              .build();
      cacheKey = key;
    }
    return cached;
  }

  /**
   * Streaming counterpart of {@link #chatModel(String)}: emits tokens incrementally so callers can
   * detect liveness (the token flow is the heartbeat) and relay progress. The model's own timeout
   * is deliberately generous — per-token idle detection is the caller's responsibility — so a long
   * but still-streaming generation is never killed mid-flight. Returns {@code null} when
   * unconfigured.
   */
  public synchronized StreamingChatModel streamingChatModel(String modelId) {
    if (!settings.isConfigured() || modelId == null || modelId.isBlank()) {
      return null;
    }
    // Absolute ceiling only — the runner aborts on token-idle well before this.
    int absoluteTimeout = Math.max(1800, settings.timeoutSeconds() * 4);
    String key =
        settings.baseUrl() + "|" + settings.apiKey() + "|" + modelId + "|s" + absoluteTimeout;
    if (!key.equals(streamCacheKey)) {
      cachedStream =
          OpenAiStreamingChatModel.builder()
              .baseUrl(settings.baseUrl())
              .apiKey(settings.apiKey())
              .modelName(modelId)
              .timeout(Duration.ofSeconds(absoluteTimeout))
              .build();
      streamCacheKey = key;
    }
    return cachedStream;
  }
}
