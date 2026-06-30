package com.platform.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.analytics.api.AnalyticsController;
import com.platform.analytics.automated.AutomatedTestController;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Locks in the Phase D3 gates on platform-analytics: project-scoped dashboards/queries require
 * VIEW_RESULTS, the lone mutating endpoint (flakiness recompute) requires OPERATE_QUALITY, and
 * org-level rollups stay ungated (no project scope to bind). Reflection-based; enforcement is
 * covered in platform-security.
 */
class AnalyticsRbacAnnotationTest {

  private static Method method(Class<?> type, String name) {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(m -> m.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no method " + name + " on " + type));
  }

  @Test
  void automatedTestCatalogIsViewScopedToProject() {
    RequireCapability rc = AutomatedTestController.class.getAnnotation(RequireCapability.class);
    assertThat(rc).isNotNull();
    assertThat(rc.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void flakinessReadViewWriteOperate() {
    RequireCapability read =
        method(AnalyticsController.class, "topFlaky").getAnnotation(RequireCapability.class);
    assertThat(read).isNotNull();
    assertThat(read.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(read.scope()).isEqualTo("projectId");

    RequireCapability write =
        method(AnalyticsController.class, "recomputeFlakiness")
            .getAnnotation(RequireCapability.class);
    assertThat(write).isNotNull();
    assertThat(write.value()).isEqualTo(Capability.OPERATE_QUALITY);
    assertThat(write.scope()).isEqualTo("projectId");
  }

  @Test
  void orgRollupStaysUngated() {
    assertThat(method(AnalyticsController.class, "orgSummary").getAnnotation(RequireCapability.class))
        .as("org/summary has no project scope to bind")
        .isNull();
  }
}
