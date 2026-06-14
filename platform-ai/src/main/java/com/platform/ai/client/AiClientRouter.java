package com.platform.ai.client;

import com.platform.ai.claude.ClaudeApiClient;
import com.platform.ai.openai.OpenAiClient;
import com.platform.core.repository.PlatformSettingRepository;
import com.platform.core.service.SettingResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Routes every AI analysis call to the provider that is currently configured in
 * platform_settings ({@code ai.provider = "claude" | "openai"}).
 *
 * <p>Marked {@code @Primary} so Spring injects this bean everywhere {@link AiClient}
 * is autowired. Both concrete clients are always registered; the active one is
 * selected at call time, so changing the provider in the Portal takes effect
 * immediately without a service restart.</p>
 */
@Primary
@Component
public class AiClientRouter implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClientRouter.class);
    private static final String KEY_PROVIDER = "ai.provider";

    private final PlatformSettingRepository settingRepo;
    private final SettingResolver settingResolver;
    private final ClaudeApiClient claudeClient;
    private final OpenAiClient openAiClient;

    public AiClientRouter(PlatformSettingRepository settingRepo,
                          SettingResolver settingResolver,
                          @Qualifier("claudeAiClient") ClaudeApiClient claudeClient,
                          @Qualifier("openAiClient")   OpenAiClient   openAiClient) {
        this.settingRepo     = settingRepo;
        this.settingResolver = settingResolver;
        this.claudeClient    = claudeClient;
        this.openAiClient    = openAiClient;
    }

    @Override
    public AiAnalysisResponse analyse(String systemPrompt, String userPrompt) {
        return active(null).analyse(systemPrompt, userPrompt);
    }

    @Override
    public AiAnalysisResponse analyse(String systemPrompt, String userPrompt, UUID projectId) {
        return active(projectId).analyse(systemPrompt, userPrompt);
    }

    @Override
    public String providerName() {
        return active(null).providerName();
    }

    @Override
    public String providerName(UUID projectId) {
        return active(projectId).providerName();
    }

    /**
     * Selects the provider for a project via the Org→Team→Project settings cascade.
     * Falls back to the global {@code ai.provider} setting, then to claude.
     */
    private AiClient active(UUID projectId) {
        String provider;
        if (projectId != null) {
            provider = settingResolver.resolveOrDefault(projectId, KEY_PROVIDER, globalProvider());
        } else {
            provider = globalProvider();
        }
        log.debug("[AI Router] Active provider for project {}: {}", projectId, provider);
        return "openai".equalsIgnoreCase(provider) ? openAiClient : claudeClient;
    }

    private String globalProvider() {
        return settingRepo.findById(KEY_PROVIDER)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse("claude");
    }
}
