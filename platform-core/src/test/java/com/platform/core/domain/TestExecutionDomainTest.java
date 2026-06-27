package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain-method behavior added for the manual test-execution feature (M1). */
class TestExecutionDomainTest {

  @Test
  void testRunReopenRestoresInProgressAndClearsCompletedAt() {
    TestRun run = new TestRun(UUID.randomUUID(), "Run", "1.0", "STAGING", "alice");
    assertThat(run.isEditable()).isTrue();

    run.complete();
    assertThat(run.getStatus()).isEqualTo("COMPLETED");
    assertThat(run.getCompletedAt()).isNotNull();
    assertThat(run.isEditable()).isFalse();

    run.reopen();
    assertThat(run.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(run.getCompletedAt()).isNull();
    assertThat(run.isEditable()).isTrue();
  }

  @Test
  void linkDefectThenClearDefect() {
    TestCaseExecution exec = new TestCaseExecution(UUID.randomUUID(), UUID.randomUUID());
    assertThat(exec.getDefectExternalId()).isNull();

    exec.linkDefect(
        "24336", "https://dev.azure.com/acme/_workitems/edit/24336", "Login bug", "Active");
    assertThat(exec.getDefectExternalId()).isEqualTo("24336");
    assertThat(exec.getDefectUrl()).endsWith("/_workitems/edit/24336");
    assertThat(exec.getDefectTitle()).isEqualTo("Login bug");
    assertThat(exec.getDefectState()).isEqualTo("Active");

    exec.clearDefect();
    assertThat(exec.getDefectExternalId()).isNull();
    assertThat(exec.getDefectUrl()).isNull();
    assertThat(exec.getDefectTitle()).isNull();
    assertThat(exec.getDefectState()).isNull();
  }

  @Test
  void executionAttachmentCarriesMetadata() {
    UUID execId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    ExecutionAttachment a =
        new ExecutionAttachment(
            execId, runId, "screenshot.png", "image/png", 1234L, "{\"key\":\"ab/abcd\"}", "bob");
    assertThat(a.getExecutionId()).isEqualTo(execId);
    assertThat(a.getTestRunId()).isEqualTo(runId);
    assertThat(a.getFileName()).isEqualTo("screenshot.png");
    assertThat(a.getContentType()).isEqualTo("image/png");
    assertThat(a.getSizeBytes()).isEqualTo(1234L);
    assertThat(a.getBlobRef()).contains("ab/abcd");
    assertThat(a.getUploadedBy()).isEqualTo("bob");
    assertThat(a.getUploadedAt()).isNotNull();
  }
}
