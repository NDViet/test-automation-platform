package com.platform.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.classification.ClaudeAnalysisResult;
import com.platform.ai.client.AiAnalysisResponse;
import com.platform.ai.client.AiClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Anthropic Claude implementation of {@link AiClient}.
 *
 * <p>Active when {@code ai.provider=claude} (the default).
 * Wraps the Anthropic Java SDK; parses the JSON response after stripping
 * any optional markdown fences. Reports real token usage from the API response.</p>
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeApiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiClient.class);
    private static final int MAX_TOKENS = 1024;

    private final ObjectMapper objectMapper;
    private final String apiKey;

    private AnthropicClient client;

    public ClaudeApiClient(ObjectMapper objectMapper,
                           @Value("${anthropic.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @PostConstruct
    void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
            log.info("[AI] Claude client initialised with explicit API key");
        } else {
            try {
                client = AnthropicOkHttpClient.fromEnv();
                log.info("[AI] Claude client initialised from ANTHROPIC_API_KEY env var");
            } catch (Exception e) {
                log.warn("[AI] ANTHROPIC_API_KEY not configured — Claude calls will return UNKNOWN");
            }
        }
    }

    @Override
    public String providerName() {
        return "claude-opus-4-6";
    }

    @Override
    public AiAnalysisResponse analyse(String systemPrompt, String userPrompt) {
        if (client == null) {
            log.warn("[AI] Claude client not initialised");
            return AiAnalysisResponse.ofResult(
                    unknownResult("Claude client not initialised — set ANTHROPIC_API_KEY"));
        }
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_OPUS_4_6)
                    .maxTokens(MAX_TOKENS)
                    .system(systemPrompt)
                    .addUserMessage(userPrompt)
                    .build();

            Message message = client.messages().create(params);

            int inputTokens  = (int) message.usage().inputTokens();
            int outputTokens = (int) message.usage().outputTokens();

            String responseText = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .findFirst()
                    .orElse("");

            log.debug("[AI] Claude token usage: input={} output={}", inputTokens, outputTokens);
            return new AiAnalysisResponse(parseResponse(responseText), inputTokens, outputTokens);

        } catch (Exception e) {
            log.error("[AI] Claude API call failed: {}", e.getMessage(), e);
            return AiAnalysisResponse.ofResult(unknownResult("API error: " + e.getMessage()));
        }
    }

    private ClaudeAnalysisResult parseResponse(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }
        try {
            return objectMapper.readValue(json, ClaudeAnalysisResult.class);
        } catch (Exception e) {
            log.error("[AI] Failed to parse Claude response: {}", e.getMessage());
            log.debug("[AI] Raw response: {}", raw);
            return unknownResult("JSON parse error: " + e.getMessage());
        }
    }

    private ClaudeAnalysisResult unknownResult(String reason) {
        return new ClaudeAnalysisResult(
                "UNKNOWN", 0.0,
                reason,
                "Automated analysis unavailable",
                "Review the failure manually or retry after configuring the API key",
                false,
                "Unknown");
    }
}
