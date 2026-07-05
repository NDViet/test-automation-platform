package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestCaseExecution;

public record TestCaseExecutionDto(
    String id,
    String testRunId,
    String testCaseId,
    String testCaseTitle,
    String status,
    String actualResult,
    String notes,
    String executedBy,
    String executedAt,
    String createdAt,
    String defectId,
    String defectUrl,
    String defectTitle,
    String defectState,
    String assigneeUserId,
    String assigneeUsername) {

  public static TestCaseExecutionDto from(TestCaseExecution exec, String testCaseTitle) {
    return from(exec, testCaseTitle, null);
  }

  public static TestCaseExecutionDto from(
      TestCaseExecution exec, String testCaseTitle, String assigneeUsername) {
    return new TestCaseExecutionDto(
        exec.getId() != null ? exec.getId().toString() : null,
        exec.getTestRunId() != null ? exec.getTestRunId().toString() : null,
        exec.getTestCaseId() != null ? exec.getTestCaseId().toString() : null,
        testCaseTitle,
        exec.getStatus(),
        exec.getActualResult(),
        exec.getNotes(),
        exec.getExecutedBy(),
        exec.getExecutedAt() != null ? exec.getExecutedAt().toString() : null,
        exec.getCreatedAt() != null ? exec.getCreatedAt().toString() : null,
        exec.getDefectExternalId(),
        exec.getDefectUrl(),
        exec.getDefectTitle(),
        exec.getDefectState(),
        exec.getAssigneeUserId() != null ? exec.getAssigneeUserId().toString() : null,
        assigneeUsername);
  }
}
