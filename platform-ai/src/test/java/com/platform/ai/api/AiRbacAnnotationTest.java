package com.platform.ai.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Verifies the AI endpoints carry the right capability gates (Phase D1):
 *
 * <ul>
 *   <li>the LiteLLM gateway settings + on-demand batch require {@code MANAGE_AI_GATEWAY} (SUPER),
 *   <li>analysis reads require {@code VIEW_RESULTS} scoped to the project,
 *   <li>on-demand single-result classification requires {@code OPERATE_QUALITY} scoped to the
 *       project.
 * </ul>
 *
 * <p>Reflection-based so it stays a fast unit test (no Spring context); the aspect that enforces
 * these annotations is covered in platform-security.
 */
class AiRbacAnnotationTest {

  private static RequireCapability gate(Class<?> type, String method, Class<?>... params) {
    try {
      Method m = type.getDeclaredMethod(method, params);
      RequireCapability rc = m.getAnnotation(RequireCapability.class);
      assertThat(rc).as("%s.%s must carry @RequireCapability", type.getSimpleName(), method)
          .isNotNull();
      return rc;
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void gatewaySettingsRequireSuperAdmin() {
    assertThat(gate(AiSettingsController.class, "getSettings").value())
        .isEqualTo(Capability.MANAGE_AI_GATEWAY);
    assertThat(
            gate(AiSettingsController.class, "updateSettings", AiSettingsController.AiSettingsUpdate.class)
                .value())
        .isEqualTo(Capability.MANAGE_AI_GATEWAY);
    assertThat(
            gate(
                    AiSettingsController.class,
                    "testConnection",
                    AiSettingsController.TestConnectionRequest.class)
                .value())
        .isEqualTo(Capability.MANAGE_AI_GATEWAY);
    assertThat(
            gate(
                    AiSettingsController.class,
                    "fetchModels",
                    AiSettingsController.TestConnectionRequest.class)
                .value())
        .isEqualTo(Capability.MANAGE_AI_GATEWAY);
  }

  @Test
  void batchRunNowRequiresSuperAdmin() {
    assertThat(gate(AiAnalysisController.class, "runNow", int.class).value())
        .isEqualTo(Capability.MANAGE_AI_GATEWAY);
  }

  @Test
  void analysisReadsRequireViewScopedToProject() {
    RequireCapability list =
        gate(
            AiAnalysisController.class,
            "listAnalyses",
            java.util.UUID.class,
            String.class,
            int.class);
    assertThat(list.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(list.scope()).isEqualTo("projectId");
  }

  @Test
  void analyseResultRequiresOperateScopedToProject() {
    RequireCapability rc =
        gate(
            AiAnalysisController.class,
            "analyseResult",
            java.util.UUID.class,
            java.util.UUID.class);
    assertThat(rc.value()).isEqualTo(Capability.OPERATE_QUALITY);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void scopedOverridesGuarded() {
    RequireCapability eff =
        gate(ScopedAiSettingsController.class, "effective", java.util.UUID.class);
    assertThat(eff.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(eff.scope()).isEqualTo("projectId");

    RequireCapability set =
        gate(
            ScopedAiSettingsController.class,
            "set",
            String.class,
            java.util.UUID.class,
            ScopedAiSettingsController.SettingUpdate.class);
    assertThat(set.value()).isEqualTo(Capability.MANAGE_PROJECT);
    assertThat(set.scope()).isEqualTo("scopeId");
  }
}
