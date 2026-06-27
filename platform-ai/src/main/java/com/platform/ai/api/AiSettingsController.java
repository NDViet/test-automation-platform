package com.platform.ai.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.PlatformSetting;
import com.platform.core.repository.PlatformSettingRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CRUD for the LiteLLM gateway settings stored in {@code platform_settings}.
 *
 * <p>The platform reaches every model through one LiteLLM (OpenAI-compatible) endpoint, so settings
 * are a base URL + key + a named model list + per-role model ids — the same shape developers use to
 * point OpenCode / Claude Code Router / VS Code chat at LiteLLM.
 *
 * <ul>
 *   <li>GET /api/v1/ai/settings — read settings (api-key never returned, only {@code
 *       liteLlmKeySet})
 *   <li>PUT /api/v1/ai/settings — update settings
 *   <li>POST /api/v1/ai/settings/test — probe the LiteLLM endpoint
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai/settings")
public class AiSettingsController {

  private static final Logger log = LoggerFactory.getLogger(AiSettingsController.class);

  private static final String KEY_ENABLED = "ai.enabled";
  private static final String KEY_REALTIME = "ai.realtime.enabled";
  private static final String KEY_BASE_URL = "ai.litellm.base-url";
  private static final String KEY_API_KEY = "ai.litellm.api-key";
  private static final String KEY_MODELS = "ai.litellm.models";
  private static final String KEY_MODEL_ANALYSIS = "ai.litellm.model.analysis";
  private static final String KEY_MODEL_STANDARD = "ai.litellm.model.standard";
  private static final String KEY_MODEL_COMPLEX = "ai.litellm.model.complex";
  private static final String KEY_MODEL_SUMMARIZER = "ai.litellm.model.summarizer";

  private static final String DEFAULT_STANDARD = "claude-sonnet-4-6";
  private static final String DEFAULT_COMPLEX = "claude-opus-4-6";
  private static final String DEFAULT_SUMMARIZER = "claude-haiku-4-5";

  private final PlatformSettingRepository repo;
  private final ObjectMapper mapper;
  private final com.platform.llm.LlmSettings llmSettings;

  public AiSettingsController(
      PlatformSettingRepository repo,
      ObjectMapper mapper,
      com.platform.llm.LlmSettings llmSettings) {
    this.repo = repo;
    this.mapper = mapper;
    this.llmSettings = llmSettings;
  }

  /** A model offered by the configured LiteLLM gateway. */
  public record ModelEntry(String id, String label) {}

  @GetMapping
  public Map<String, Object> getSettings() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("enabled", Boolean.parseBoolean(get(KEY_ENABLED, "false")));
    result.put("realtimeEnabled", Boolean.parseBoolean(get(KEY_REALTIME, "false")));
    // Effective values: DB setting first, else the env default the runtime client uses.
    String baseUrl = llmSettings.baseUrl();
    result.put("liteLlmBaseUrl", baseUrl != null ? baseUrl : "");
    result.put("liteLlmKeySet", llmSettings.apiKey() != null);
    result.put("models", parseModels(get(KEY_MODELS, "[]")));
    result.put("modelAnalysis", get(KEY_MODEL_ANALYSIS, DEFAULT_STANDARD));
    result.put("modelStandard", get(KEY_MODEL_STANDARD, DEFAULT_STANDARD));
    result.put("modelComplex", get(KEY_MODEL_COMPLEX, DEFAULT_COMPLEX));
    result.put("modelSummarizer", get(KEY_MODEL_SUMMARIZER, DEFAULT_SUMMARIZER));
    return result;
  }

  public record AiSettingsUpdate(
      Boolean enabled,
      Boolean realtimeEnabled,
      String liteLlmBaseUrl,
      String liteLlmApiKey,
      List<ModelEntry> models,
      String modelAnalysis,
      String modelStandard,
      String modelComplex,
      String modelSummarizer) {}

  @PutMapping
  public Map<String, Object> updateSettings(@RequestBody AiSettingsUpdate body) {
    if (body.enabled() != null) save(KEY_ENABLED, body.enabled().toString());
    if (body.realtimeEnabled() != null) save(KEY_REALTIME, body.realtimeEnabled().toString());
    if (notBlank(body.liteLlmBaseUrl())) save(KEY_BASE_URL, body.liteLlmBaseUrl().trim());
    // Only overwrite the key when a new non-blank value is supplied (blank = keep current).
    if (notBlank(body.liteLlmApiKey())) save(KEY_API_KEY, body.liteLlmApiKey().trim());
    if (body.models() != null) save(KEY_MODELS, writeModels(body.models()));
    if (notBlank(body.modelAnalysis())) save(KEY_MODEL_ANALYSIS, body.modelAnalysis().trim());
    if (notBlank(body.modelStandard())) save(KEY_MODEL_STANDARD, body.modelStandard().trim());
    if (notBlank(body.modelComplex())) save(KEY_MODEL_COMPLEX, body.modelComplex().trim());
    if (notBlank(body.modelSummarizer())) save(KEY_MODEL_SUMMARIZER, body.modelSummarizer().trim());
    return getSettings();
  }

  public record TestConnectionRequest(String liteLlmBaseUrl, String liteLlmApiKey) {}

  public record TestConnectionResult(boolean success, String message) {}

  @PostMapping("/test")
  public ResponseEntity<TestConnectionResult> testConnection(
      @RequestBody(required = false) TestConnectionRequest req) {
    // Fall back to the effective (DB-or-env) config the runtime client actually uses.
    String baseUrl =
        req != null && notBlank(req.liteLlmBaseUrl())
            ? req.liteLlmBaseUrl().trim()
            : llmSettings.baseUrl();
    String key =
        req != null && notBlank(req.liteLlmApiKey())
            ? req.liteLlmApiKey().trim()
            : llmSettings.apiKey();

    if (!notBlank(baseUrl)) {
      return ResponseEntity.ok(
          new TestConnectionResult(false, "LiteLLM base URL is not configured"));
    }
    if (!notBlank(key)) {
      return ResponseEntity.ok(
          new TestConnectionResult(false, "LiteLLM API key is not configured"));
    }
    try {
      boolean ok = probeModels(baseUrl, key);
      return ResponseEntity.ok(
          new TestConnectionResult(
              ok, ok ? "Connection successful" : "LiteLLM returned an error from /models"));
    } catch (Exception e) {
      String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      // A common footgun: from inside the container, localhost is the app itself — not the gateway.
      if (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
        detail +=
            " (note: this test runs server-side; use the gateway's network host, e.g."
                + " http://litellm:4000/v1, not localhost)";
      }
      return ResponseEntity.ok(new TestConnectionResult(false, "Connection failed: " + detail));
    }
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private List<ModelEntry> parseModels(String json) {
    try {
      ModelEntry[] arr = mapper.readValue(json, ModelEntry[].class);
      return List.of(arr);
    } catch (Exception e) {
      log.warn("[AI] Could not parse {} as a model list: {}", KEY_MODELS, e.getMessage());
      return List.of();
    }
  }

  private String writeModels(List<ModelEntry> models) {
    try {
      return mapper.writeValueAsString(models);
    } catch (Exception e) {
      return "[]";
    }
  }

  private String get(String key, String defaultValue) {
    String v = value(key);
    return v != null ? v : defaultValue;
  }

  private String value(String key) {
    return repo.findById(key)
        .map(PlatformSetting::getValue)
        .filter(v -> v != null && !v.isBlank())
        .orElse(null);
  }

  private void save(String key, String value) {
    PlatformSetting s = repo.findById(key).orElse(new PlatformSetting(key, value));
    s.setValue(value);
    repo.save(s);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  /** GET {baseUrl}/models with a Bearer key; 2xx means the gateway is reachable and authorized. */
  private boolean probeModels(String baseUrl, String key) throws Exception {
    String url = baseUrl.replaceAll("/+$", "") + "/models";
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + key)
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build();
    HttpResponse<String> resp =
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    return resp.statusCode() >= 200 && resp.statusCode() < 300;
  }
}
