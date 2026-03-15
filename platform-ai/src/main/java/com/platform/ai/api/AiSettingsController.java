package com.platform.ai.api;

import com.platform.core.domain.PlatformSetting;
import com.platform.core.repository.PlatformSettingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CRUD for AI provider settings stored in platform_settings.
 *
 * GET  /api/v1/ai/settings          — read current settings (api-key masked)
 * PUT  /api/v1/ai/settings          — update settings
 * POST /api/v1/ai/settings/test     — test API key connectivity
 */
@RestController
@RequestMapping("/api/v1/ai/settings")
public class AiSettingsController {

    private static final String KEY_ENABLED  = "ai.enabled";
    private static final String KEY_REALTIME = "ai.realtime.enabled";
    private static final String KEY_PROVIDER = "ai.provider";
    private static final String KEY_MODEL    = "ai.model";
    private static final String KEY_API_KEY  = "ai.api-key";

    private final PlatformSettingRepository repo;

    public AiSettingsController(PlatformSettingRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled",         Boolean.parseBoolean(get(KEY_ENABLED,  "false")));
        result.put("realtimeEnabled", Boolean.parseBoolean(get(KEY_REALTIME, "false")));
        result.put("provider", get(KEY_PROVIDER, "anthropic"));
        result.put("model",    get(KEY_MODEL,    "claude-sonnet-4-6"));
        // Mask the API key — return only whether it is set
        String raw = get(KEY_API_KEY, "");
        result.put("apiKeySet", raw != null && !raw.isBlank());
        return result;
    }

    public record AiSettingsUpdate(Boolean enabled, Boolean realtimeEnabled, String provider, String model, String apiKey) {}

    @PutMapping
    public Map<String, Object> updateSettings(@RequestBody AiSettingsUpdate body) {
        if (body.enabled()         != null) save(KEY_ENABLED,  body.enabled().toString());
        if (body.realtimeEnabled() != null) save(KEY_REALTIME, body.realtimeEnabled().toString());
        if (body.provider() != null && !body.provider().isBlank()) save(KEY_PROVIDER, body.provider());
        if (body.model()    != null && !body.model().isBlank())    save(KEY_MODEL,    body.model());
        if (body.apiKey()   != null && !body.apiKey().isBlank())   save(KEY_API_KEY,  body.apiKey());
        return getSettings();
    }

    public record TestConnectionRequest(String provider, String model, String apiKey) {}
    public record TestConnectionResult(boolean success, String message) {}

    @PostMapping("/test")
    public ResponseEntity<TestConnectionResult> testConnection(@RequestBody TestConnectionRequest req) {
        String key      = req.apiKey() != null && !req.apiKey().isBlank() ? req.apiKey() : get(KEY_API_KEY, "");
        String provider = req.provider() != null ? req.provider() : get(KEY_PROVIDER, "anthropic");
        String model    = req.model()    != null ? req.model()    : get(KEY_MODEL, "claude-sonnet-4-6");

        if (key == null || key.isBlank()) {
            return ResponseEntity.ok(new TestConnectionResult(false, "API key is not configured"));
        }

        try {
            boolean ok = "openai".equalsIgnoreCase(provider)
                    ? testOpenAi(key, model)
                    : testClaude(key, model);
            return ResponseEntity.ok(new TestConnectionResult(ok, ok ? "Connection successful" : "API returned an error"));
        } catch (Exception e) {
            return ResponseEntity.ok(new TestConnectionResult(false, "Connection failed: " + e.getMessage()));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String get(String key, String defaultValue) {
        return repo.findById(key).map(PlatformSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    private void save(String key, String value) {
        PlatformSetting s = repo.findById(key).orElse(new PlatformSetting(key, value));
        s.setValue(value);
        repo.save(s);
    }

    private boolean testClaude(String apiKey, String model) throws Exception {
        String body = """
                {"model":"%s","max_tokens":5,"messages":[{"role":"user","content":"hi"}]}
                """.formatted(model).trim();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200;
    }

    private boolean testOpenAi(String apiKey, String model) throws Exception {
        String body = """
                {"model":"%s","max_tokens":5,"messages":[{"role":"user","content":"hi"}]}
                """.formatted(model).trim();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200;
    }
}
