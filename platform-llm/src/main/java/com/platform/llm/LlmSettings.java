package com.platform.llm;

import com.platform.core.domain.PlatformSetting;
import com.platform.core.repository.PlatformSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the LiteLLM gateway connection settings from {@code platform_settings}, falling back to
 * environment-provided defaults. Values are read live so changes in the Portal take effect without
 * a service restart.
 */
@Component
public class LlmSettings {

  public static final String KEY_BASE_URL = "ai.litellm.base-url";
  public static final String KEY_API_KEY = "ai.litellm.api-key";
  public static final String KEY_TIMEOUT_SECONDS = "ai.litellm.timeout-seconds";

  /** Generous default — large test-case/automation generations routinely run well over a minute. */
  private static final int DEFAULT_TIMEOUT_SECONDS = 180;

  private final PlatformSettingRepository repo;
  private final String envBaseUrl;
  private final String envApiKey;
  private final int envTimeoutSeconds;

  public LlmSettings(
      PlatformSettingRepository repo,
      @Value("${litellm.base-url:}") String envBaseUrl,
      @Value("${litellm.api-key:}") String envApiKey,
      @Value("${litellm.timeout-seconds:180}") int envTimeoutSeconds) {
    this.repo = repo;
    this.envBaseUrl = envBaseUrl;
    this.envApiKey = envApiKey;
    this.envTimeoutSeconds = envTimeoutSeconds;
  }

  /** LiteLLM OpenAI-compatible base URL (e.g. {@code http://litellm:4000/v1}), or {@code null}. */
  public String baseUrl() {
    return resolve(KEY_BASE_URL, envBaseUrl);
  }

  /** LiteLLM master/virtual key, or {@code null} if unset. */
  public String apiKey() {
    return resolve(KEY_API_KEY, envApiKey);
  }

  /**
   * Model id for the given setting key (e.g. {@code ai.litellm.model.analysis}), or the fallback.
   */
  public String model(String settingKey, String fallback) {
    String v = get(settingKey);
    return v != null ? v : fallback;
  }

  /** True when both a base URL and an API key are configured. */
  public boolean isConfigured() {
    return notBlank(baseUrl()) && notBlank(apiKey());
  }

  /**
   * Per-request timeout (seconds) for the LLM HTTP call. DB setting {@code
   * ai.litellm.timeout-seconds} overrides the env default; invalid/blank values fall back to
   * {@value #DEFAULT_TIMEOUT_SECONDS}.
   */
  public int timeoutSeconds() {
    String v = get(KEY_TIMEOUT_SECONDS);
    if (v != null) {
      try {
        int parsed = Integer.parseInt(v.trim());
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        // fall through to env/default
      }
    }
    return envTimeoutSeconds > 0 ? envTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
  }

  private String resolve(String key, String envFallback) {
    String v = get(key);
    if (v != null) {
      return v;
    }
    return notBlank(envFallback) ? envFallback : null;
  }

  private String get(String key) {
    return repo.findById(key)
        .map(PlatformSetting::getValue)
        .filter(LlmSettings::notBlank)
        .orElse(null);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
