package com.platform.agent.node.impl;

import com.platform.agent.node.StepSummarizer;
import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compresses large tool results before they are sent back to the main orchestration model, cutting
 * prompt token usage when tools return verbose payloads. Routed through the LiteLLM gateway using
 * the configured summarizer model ({@code ai.litellm.model.summarizer}); falls back to truncation
 * when LiteLLM is not configured.
 */
@Component
public class StepSummarizerImpl implements StepSummarizer {

  private static final Logger log = LoggerFactory.getLogger(StepSummarizerImpl.class);

  private static final String KEY_MODEL_SUMMARIZER = "ai.litellm.model.summarizer";
  private static final String DEFAULT_SUMMARIZER = "claude-haiku-4-5";
  private static final int COMPRESS_THRESHOLD = 800;

  private static final String SYSTEM_PROMPT =
      "You are a concise technical summarizer. Compress the tool result to a dense "
          + "1-3 sentence summary preserving all key facts, numbers, and identifiers. No preamble.";

  private final LlmChatModelProvider provider;
  private final LlmSettings settings;

  public StepSummarizerImpl(LlmChatModelProvider provider, LlmSettings settings) {
    this.provider = provider;
    this.settings = settings;
  }

  @Override
  public String summarize(String toolName, String rawResult) {
    if (rawResult.length() < COMPRESS_THRESHOLD) {
      return rawResult;
    }

    ChatModel model = provider.chatModel(settings.model(KEY_MODEL_SUMMARIZER, DEFAULT_SUMMARIZER));
    if (model == null) {
      return truncate(rawResult);
    }

    try {
      String userContent =
          "Tool: "
              + toolName
              + "\nResult:\n"
              + rawResult.substring(0, Math.min(8000, rawResult.length()));
      ChatResponse response =
          model.chat(
              ChatRequest.builder()
                  .messages(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userContent))
                  .build());
      String text = response.aiMessage() != null ? response.aiMessage().text() : null;
      return text != null && !text.isBlank() ? text : truncate(rawResult);
    } catch (Exception e) {
      log.warn("step summarizer failed for tool '{}': {}", toolName, e.getMessage());
      return truncate(rawResult);
    }
  }

  private static String truncate(String raw) {
    return raw.substring(0, Math.min(500, raw.length())) + "... [truncated]";
  }
}
