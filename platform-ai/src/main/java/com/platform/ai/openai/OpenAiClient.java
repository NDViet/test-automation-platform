package com.platform.ai.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.classification.ClaudeAnalysisResult;
import com.platform.ai.client.AiAnalysisResponse;
import com.platform.ai.client.AiClient;
import com.platform.core.repository.PlatformSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions implementation of {@link AiClient}.
 *
 * <p>Active when {@code ai.provider=openai}.
 * Uses Spring {@link RestClient} to call {@code POST /v1/chat/completions}.
 * Requests {@code response_format: {type: "json_object"}} to guarantee valid JSON output,
 * eliminating the need for markdown-fence stripping on the happy path.
 * Reports real token usage from the API response.</p>
 */
@Component("openAiClient")
public class OpenAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_TOKENS = 1024;

    private static final String DB_KEY_NAME   = "ai.api-key";
    private static final String DB_MODEL_NAME = "ai.model";
    private static final String DEFAULT_MODEL = "gpt-4o";

    private final ObjectMapper objectMapper;
    private final String envApiKey;
    private final String envModel;
    private final PlatformSettingRepository settingRepo;

    // Cached client — rebuilt only when key or model changes
    private volatile String cachedKey   = null;
    private volatile String cachedModel = null;
    private volatile RestClient restClient = null;

    public OpenAiClient(ObjectMapper objectMapper,
                        @Value("${openai.api-key:}") String envApiKey,
                        @Value("${openai.model:gpt-4o}") String envModel,
                        PlatformSettingRepository settingRepo) {
        this.objectMapper = objectMapper;
        this.envApiKey    = envApiKey;
        this.envModel     = envModel;
        this.settingRepo  = settingRepo;
    }

    private String resolveApiKey() {
        String dbKey = settingRepo.findById(DB_KEY_NAME)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
        if (dbKey != null) return dbKey;
        if (envApiKey != null && !envApiKey.isBlank()) return envApiKey;
        return null;
    }

    private String resolveModel() {
        return settingRepo.findById(DB_MODEL_NAME)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(envModel != null && !envModel.isBlank() ? envModel : DEFAULT_MODEL);
    }

    private synchronized RestClient getRestClient() {
        String key   = resolveApiKey();
        String model = resolveModel();
        if (key == null || key.isBlank()) {
            log.warn("[AI] No OpenAI API key configured — set it in AI Settings or via OPENAI_API_KEY");
            return null;
        }
        if (!key.equals(cachedKey) || !model.equals(cachedModel)) {
            log.info("[AI] OpenAI config changed — rebuilding REST client (model={})", model);
            cachedKey   = key;
            cachedModel = model;
            restClient  = RestClient.builder()
                    .baseUrl(COMPLETIONS_URL)
                    .defaultHeader("Authorization", "Bearer " + key)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }
        return restClient;
    }

    @Override
    public String providerName() {
        return "openai/" + resolveModel();
    }

    @Override
    public AiAnalysisResponse analyse(String systemPrompt, String userPrompt) {
        RestClient client = getRestClient();
        if (client == null) {
            throw new IllegalStateException("OpenAI API key is not configured — set it in AI Settings");
        }

        String model = resolveModel();
        Map<String, Object> request = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userPrompt)
                )
        );

        try {
            String responseBody = client.post()
                    .body(objectMapper.writeValueAsString(request))
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (Exception e) {
            log.error("[AI] OpenAI API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }

    private AiAnalysisResponse parseResponse(String rawBody) {
        try {
            OpenAiResponse response = objectMapper.readValue(rawBody, OpenAiResponse.class);

            // Extract token counts
            int inputTokens  = response.usage() != null ? response.usage().promptTokens()     : 0;
            int outputTokens = response.usage() != null ? response.usage().completionTokens() : 0;
            log.debug("[AI] OpenAI token usage: input={} output={}", inputTokens, outputTokens);

            // Extract content
            String content = response.choices() != null && !response.choices().isEmpty()
                    ? response.choices().get(0).message().content()
                    : "";

            // Defensive fence strip (json_object mode normally skips this)
            String json = content.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf('{');
                int end   = json.lastIndexOf('}');
                if (start >= 0 && end > start) json = json.substring(start, end + 1);
            }

            ClaudeAnalysisResult result = objectMapper.readValue(json, ClaudeAnalysisResult.class);
            return new AiAnalysisResponse(result, inputTokens, outputTokens);

        } catch (Exception e) {
            log.error("[AI] Failed to parse OpenAI response: {}", e.getMessage());
            log.debug("[AI] Raw body: {}", rawBody);
            return AiAnalysisResponse.ofResult(unknownResult("JSON parse error: " + e.getMessage()));
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

    // ── Internal response model ───────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiResponse(List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(ChatMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("prompt_tokens")     int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens
    ) {}
}
