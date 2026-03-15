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
import com.platform.core.repository.PlatformSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Anthropic Claude implementation of {@link AiClient}.
 *
 * <p>Active when {@code ai.provider=claude} (the default).
 * Wraps the Anthropic Java SDK; parses the JSON response after stripping
 * any optional markdown fences. Reports real token usage from the API response.</p>
 */
@Component("claudeAiClient")
public class ClaudeApiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiClient.class);
    private static final int MAX_TOKENS = 1024;

    private static final String DB_KEY_NAME = "ai.api-key";

    private final ObjectMapper objectMapper;
    private final String envApiKey;          // from application.yml / ANTHROPIC_API_KEY env var
    private final PlatformSettingRepository settingRepo;

    // Cached client — rebuilt only when the resolved key changes
    private volatile String cachedKey    = null;
    private volatile AnthropicClient client = null;

    public ClaudeApiClient(ObjectMapper objectMapper,
                           @Value("${anthropic.api-key:}") String envApiKey,
                           PlatformSettingRepository settingRepo) {
        this.objectMapper  = objectMapper;
        this.envApiKey     = envApiKey;
        this.settingRepo   = settingRepo;
    }

    /**
     * Resolves the API key to use, in priority order:
     * 1. Key saved via Portal (platform_settings table) — updated at runtime
     * 2. Key from application.yml / ANTHROPIC_API_KEY env var — set at startup
     */
    private String resolveApiKey() {
        String dbKey = settingRepo.findById(DB_KEY_NAME)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
        if (dbKey != null) return dbKey;
        if (envApiKey != null && !envApiKey.isBlank()) return envApiKey;
        return null;
    }

    /** Returns a cached client, rebuilding it only if the active key has changed. */
    private synchronized AnthropicClient getClient() {
        String key = resolveApiKey();
        if (key == null || key.isBlank()) {
            log.warn("[AI] No API key configured — set it in AI Settings or via ANTHROPIC_API_KEY");
            return null;
        }
        if (!key.equals(cachedKey)) {
            log.info("[AI] API key changed — rebuilding Claude client");
            cachedKey = key;
            client = AnthropicOkHttpClient.builder().apiKey(key).build();
        }
        return client;
    }

    @Override
    public String providerName() {
        return "claude-opus-4-6";
    }

    @Override
    public AiAnalysisResponse analyse(String systemPrompt, String userPrompt) {
        AnthropicClient c = getClient();
        if (c == null) {
            throw new IllegalStateException("Claude API key is not configured — set it in AI Settings");
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_6)
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .build();

        // Let SDK exceptions propagate — callers (FailureClassificationService) catch and
        // persist an ERROR record so the analysis can be retried once the key is corrected.
        Message message = c.messages().create(params);

        int inputTokens  = (int) message.usage().inputTokens();
        int outputTokens = (int) message.usage().outputTokens();

        String responseText = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElse("");

        log.debug("[AI] Claude token usage: input={} output={}", inputTokens, outputTokens);
        return new AiAnalysisResponse(parseResponse(responseText), inputTokens, outputTokens);
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
