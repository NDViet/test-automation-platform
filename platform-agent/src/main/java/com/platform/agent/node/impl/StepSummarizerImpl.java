package com.platform.agent.node.impl;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.platform.agent.node.StepSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compresses large tool results using claude-haiku-4-5 before they are sent back
 * to the main orchestration model. This reduces prompt token usage significantly
 * when tools return verbose JSON payloads or long text responses.
 */
@Component
public class StepSummarizerImpl implements StepSummarizer {

    private static final Logger log = LoggerFactory.getLogger(StepSummarizerImpl.class);

    private static final String SYSTEM_PROMPT =
            "You are a concise technical summarizer. Compress the tool result to a dense " +
            "1-3 sentence summary preserving all key facts, numbers, and identifiers. No preamble.";

    private final String apiKey;
    private final int maxSummaryTokens;

    private volatile AnthropicClient client;

    public StepSummarizerImpl(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${platform.agent.summarizer.max-tokens:200}") int maxSummaryTokens) {
        this.apiKey          = apiKey;
        this.maxSummaryTokens = maxSummaryTokens;
    }

    @Override
    public String summarize(String toolName, String rawResult) {
        // Short results are not worth compressing
        if (rawResult.length() < 800) {
            return rawResult;
        }

        // No API key configured — fall back to truncation
        if (apiKey == null || apiKey.isBlank()) {
            return rawResult.substring(0, Math.min(500, rawResult.length())) + "... [truncated]";
        }

        try {
            AnthropicClient claude = getClient();
            String userContent = "Tool: " + toolName + "\nResult:\n"
                    + rawResult.substring(0, Math.min(8000, rawResult.length()));

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_HAIKU_4_5)
                    .maxTokens(maxSummaryTokens)
                    .system(SYSTEM_PROMPT)
                    .messages(List.of(
                            MessageParam.builder()
                                    .role(MessageParam.Role.USER)
                                    .content(userContent)
                                    .build()
                    ))
                    .build();

            Message response = claude.messages().create(params);
            return response.content().stream()
                    .filter(ContentBlock::isText)
                    .map(b -> b.asText().text())
                    .findFirst()
                    .orElse(rawResult.substring(0, Math.min(500, rawResult.length())) + "... [truncated]");

        } catch (Exception e) {
            log.warn("step summarizer failed for tool '{}': {}", toolName, e.getMessage());
            return rawResult.substring(0, Math.min(500, rawResult.length())) + " [truncated]";
        }
    }

    private synchronized AnthropicClient getClient() {
        if (client == null) {
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
        return client;
    }
}
