package com.platform.ai.client;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.platform.ai.claude.ClaudeApiClient;
import com.platform.ai.openai.OpenAiClient;
import com.platform.core.domain.PlatformSetting;
import com.platform.core.repository.PlatformSettingRepository;
import com.platform.core.service.SettingResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiClientRouterTest {

  private PlatformSettingRepository settingRepo;
  private SettingResolver settingResolver;
  private ClaudeApiClient claude;
  private OpenAiClient openai;
  private AiClientRouter router;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    settingRepo = mock(PlatformSettingRepository.class);
    settingResolver = mock(SettingResolver.class);
    claude = mock(ClaudeApiClient.class);
    openai = mock(OpenAiClient.class);
    router = new AiClientRouter(settingRepo, settingResolver, claude, openai);
  }

  private void globalProvider(String value) {
    PlatformSetting s = mock(PlatformSetting.class);
    when(s.getValue()).thenReturn(value);
    when(settingRepo.findById("ai.provider")).thenReturn(Optional.of(s));
  }

  @Test
  void projectScoped_routesToOpenAiWhenCascadeSaysOpenai() {
    when(settingResolver.resolveOrDefault(eq(projectId), eq("ai.provider"), anyString()))
        .thenReturn("openai");

    router.analyse("sys", "user", projectId);

    verify(openai).analyse("sys", "user");
    verify(claude, never()).analyse(any(), any());
  }

  @Test
  void projectScoped_routesToClaudeWhenCascadeSaysClaude() {
    when(settingResolver.resolveOrDefault(eq(projectId), eq("ai.provider"), anyString()))
        .thenReturn("claude");

    router.analyse("sys", "user", projectId);

    verify(claude).analyse("sys", "user");
    verify(openai, never()).analyse(any(), any());
  }

  @Test
  void noProject_usesGlobalProvider() {
    globalProvider("openai");

    router.analyse("sys", "user");

    verify(openai).analyse("sys", "user");
    verify(claude, never()).analyse(any(), any());
  }
}
