package com.platform.ai.client;

import com.platform.ai.claude.ClaudeApiClient;
import com.platform.ai.openai.OpenAiClient;
import com.platform.core.repository.PlatformSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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
    private final ClaudeApiClient claudeClient;
    private final OpenAiClient openAiClient;

    public AiClientRouter(PlatformSettingRepository settingRepo,
                          @Qualifier("claudeAiClient") ClaudeApiClient claudeClient,
                          @Qualifier("openAiClient")   OpenAiClient   openAiClient) {
        this.settingRepo  = settingRepo;
        this.claudeClient = claudeClient;
        this.openAiClient = openAiClient;
    }

    @Override
    public AiAnalysisResponse analyse(String systemPrompt, String userPrompt) {
        return active().analyse(systemPrompt, userPrompt);
    }

    @Override
    public String providerName() {
        return active().providerName();
    }

    private AiClient active() {
        String provider = settingRepo.findById(KEY_PROVIDER)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse("claude");
        log.debug("[AI Router] Active provider: {}", provider);
        return "openai".equalsIgnoreCase(provider) ? openAiClient : claudeClient;
    }
}
