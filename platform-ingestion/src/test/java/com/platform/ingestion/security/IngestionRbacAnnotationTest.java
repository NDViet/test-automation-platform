package com.platform.ingestion.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.ingestion.dashboard.QualityDashboardController;
import com.platform.ingestion.management.AdoOnboardingController;
import com.platform.ingestion.management.CredentialKeyController;
import com.platform.ingestion.management.IntegrationConfigController;
import com.platform.ingestion.management.tcm.TestCaseManagementController;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Locks in the Phase D2 capability gates on platform-ingestion: ADO structure import and the
 * platform credential-key are super-admin only; project-scoped controllers default to VIEW with
 * OPERATE on mutations. Reflection-based (fast, no Spring context) — the aspect that enforces these
 * is covered in platform-security.
 */
class IngestionRbacAnnotationTest {

  private static RequireCapability classGate(Class<?> type) {
    RequireCapability rc = type.getAnnotation(RequireCapability.class);
    assertThat(rc)
        .as("%s must carry a class-level @RequireCapability", type.getSimpleName())
        .isNotNull();
    return rc;
  }

  private static RequireCapability methodGate(Class<?> type, String method) {
    Method m =
        Arrays.stream(type.getDeclaredMethods())
            .filter(x -> x.getName().equals(method))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no method " + method + " on " + type));
    RequireCapability rc = m.getAnnotation(RequireCapability.class);
    assertThat(rc)
        .as("%s.%s must carry @RequireCapability", type.getSimpleName(), method)
        .isNotNull();
    return rc;
  }

  @Test
  void adoImportRequiresSuperAdmin() {
    RequireCapability rc = classGate(AdoOnboardingController.class);
    assertThat(rc.value()).isEqualTo(Capability.IMPORT_ADO_STRUCTURE);
    assertThat(rc.scope()).isBlank();
  }

  @Test
  void credentialKeyRequiresSuperAdmin() {
    RequireCapability rc = classGate(CredentialKeyController.class);
    assertThat(rc.value()).isEqualTo(Capability.MANAGE_PLATFORM);
    assertThat(rc.scope()).isBlank();
  }

  @Test
  void dashboardDefaultsToViewScopedToProject() {
    RequireCapability rc = classGate(QualityDashboardController.class);
    assertThat(rc.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void integrationConfigRequiresProjectAdmin() {
    RequireCapability rc = classGate(IntegrationConfigController.class);
    assertThat(rc.value()).isEqualTo(Capability.MANAGE_PROJECT);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void testCaseControllerReadsViewWritesOperate() {
    RequireCapability cls = classGate(TestCaseManagementController.class);
    assertThat(cls.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(cls.scope()).isEqualTo("projectId");

    // A representative mutating endpoint must override to OPERATE_QUALITY.
    RequireCapability create = methodGate(TestCaseManagementController.class, "create");
    assertThat(create.value()).isEqualTo(Capability.OPERATE_QUALITY);
    assertThat(create.scope()).isEqualTo("projectId");
  }

  @Test
  void failureClusterControllerReadsViewWritesOperate() {
    RequireCapability cls =
        classGate(com.platform.ingestion.quality.FailureClusterController.class);
    assertThat(cls.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(cls.scope()).isEqualTo("projectId");

    for (String method : new String[] {"updateStatus", "linkDefect", "createDefect", "retest"}) {
      RequireCapability rc =
          methodGate(com.platform.ingestion.quality.FailureClusterController.class, method);
      assertThat(rc.value())
          .as("%s must require OPERATE_QUALITY", method)
          .isEqualTo(Capability.OPERATE_QUALITY);
      assertThat(rc.scope()).isEqualTo("projectId");
    }
  }

  @Test
  void runScheduleControllerRequiresProjectAdmin() {
    RequireCapability rc =
        classGate(com.platform.ingestion.management.schedule.RunScheduleController.class);
    assertThat(rc.value()).isEqualTo(Capability.MANAGE_PROJECT);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void datasetControllerReadsViewWritesOperate() {
    Class<?> type = com.platform.ingestion.management.dataset.DatasetController.class;
    RequireCapability cls = classGate(type);
    assertThat(cls.value()).isEqualTo(Capability.VIEW_RESULTS);
    RequireCapability create = methodGate(type, "create");
    assertThat(create.value()).isEqualTo(Capability.OPERATE_QUALITY);
    assertThat(create.scope()).isEqualTo("projectId");
  }

  @Test
  void sharedStepControllerReadsViewWritesOperate() {
    Class<?> type = com.platform.ingestion.management.sharedstep.SharedStepController.class;
    RequireCapability cls = classGate(type);
    assertThat(cls.value()).isEqualTo(Capability.VIEW_RESULTS);
    RequireCapability create = methodGate(type, "create");
    assertThat(create.value()).isEqualTo(Capability.OPERATE_QUALITY);
    assertThat(create.scope()).isEqualTo("projectId");
  }

  @Test
  void notificationPrefControllerRequiresProjectAdmin() {
    RequireCapability rc =
        classGate(com.platform.ingestion.management.notification.NotificationPrefController.class);
    assertThat(rc.value()).isEqualTo(Capability.MANAGE_PROJECT);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void reportExportsRequireView() {
    RequireCapability rc = classGate(com.platform.ingestion.management.tcm.ReportController.class);
    assertThat(rc.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void auditLogReadsRequireView() {
    RequireCapability rc = classGate(com.platform.ingestion.audit.AuditLogController.class);
    assertThat(rc.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void executionPlanningReadsRequireView() {
    RequireCapability rc =
        classGate(com.platform.ingestion.management.tcm.ExecutionPlanningController.class);
    assertThat(rc.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(rc.scope()).isEqualTo("projectId");
  }

  @Test
  void qualityGateControllerGuardsConfigAndOverride() {
    Class<?> type = com.platform.ingestion.quality.QualityGateController.class;
    RequireCapability cls = classGate(type);
    assertThat(cls.value()).isEqualTo(Capability.VIEW_RESULTS);
    assertThat(cls.scope()).isEqualTo("projectId");

    // Config write + override are project-admin; evaluate is a quality operation.
    for (String method : new String[] {"updateConfig", "overrideGate"}) {
      RequireCapability rc = methodGate(type, method);
      assertThat(rc.value()).as("%s", method).isEqualTo(Capability.MANAGE_PROJECT);
      assertThat(rc.scope()).isEqualTo("projectId");
    }
    RequireCapability eval = methodGate(type, "evaluateGate");
    assertThat(eval.value()).isEqualTo(Capability.OPERATE_QUALITY);
  }
}
