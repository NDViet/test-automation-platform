package com.platform.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.api.AiSettingsController.AiSettingsUpdate;
import com.platform.ai.api.AiSettingsController.ModelEntry;
import com.platform.core.domain.PlatformSetting;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiSettingsControllerTest {

  @Mock com.platform.core.repository.PlatformSettingRepository repo;
  @Mock com.platform.llm.LlmSettings llmSettings;
  final ObjectMapper mapper = new ObjectMapper();
  AiSettingsController controller;

  @BeforeEach
  void setUp() {
    controller = new AiSettingsController(repo, mapper, llmSettings);
    lenient().when(repo.findById(anyString())).thenReturn(Optional.empty());
  }

  private static PlatformSetting setting(String key, String value) {
    PlatformSetting s = new PlatformSetting(key, value);
    s.setValue(value);
    return s;
  }

  @Test
  void getReturnsBaseUrlAndModelsButMasksKey() {
    // Effective base URL / key come from LlmSettings (DB-or-env), not the repo directly.
    when(llmSettings.baseUrl()).thenReturn("http://litellm:4000/v1");
    when(llmSettings.apiKey()).thenReturn("sk-secret");
    when(repo.findById("ai.litellm.models"))
        .thenReturn(
            Optional.of(
                setting(
                    "ai.litellm.models", "[{\"id\":\"claude-sonnet-4-6\",\"label\":\"Sonnet\"}]")));

    Map<String, Object> out = controller.getSettings();

    assertThat(out.get("liteLlmBaseUrl")).isEqualTo("http://litellm:4000/v1");
    assertThat(out.get("liteLlmKeySet")).isEqualTo(true);
    assertThat(out).doesNotContainValue("sk-secret");
    @SuppressWarnings("unchecked")
    List<ModelEntry> models = (List<ModelEntry>) out.get("models");
    assertThat(models).extracting(ModelEntry::id).containsExactly("claude-sonnet-4-6");
  }

  @Test
  void getReportsKeyNotSetWhenAbsent() {
    Map<String, Object> out = controller.getSettings();
    assertThat(out.get("liteLlmKeySet")).isEqualTo(false);
  }

  @Test
  void updatePersistsLiteLlmFields() {
    AiSettingsUpdate body =
        new AiSettingsUpdate(
            true,
            false,
            "http://litellm:4000/v1",
            "sk-new",
            List.of(new ModelEntry("gpt-4o", "GPT-4o")),
            "gpt-4o",
            "claude-sonnet-4-6",
            "claude-opus-4-6",
            "claude-haiku-4-5");

    controller.updateSettings(body);

    ArgumentCaptor<PlatformSetting> captor = ArgumentCaptor.forClass(PlatformSetting.class);
    verify(repo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    Map<String, String> saved =
        captor.getAllValues().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    PlatformSetting::getKey, PlatformSetting::getValue, (a, b) -> b));

    assertThat(saved).containsEntry("ai.litellm.base-url", "http://litellm:4000/v1");
    assertThat(saved).containsEntry("ai.litellm.api-key", "sk-new");
    assertThat(saved).containsEntry("ai.litellm.model.analysis", "gpt-4o");
    assertThat(saved.get("ai.litellm.models")).contains("gpt-4o").contains("GPT-4o");
  }
}
