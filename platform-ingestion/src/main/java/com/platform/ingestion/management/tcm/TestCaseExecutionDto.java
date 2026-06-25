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
    String createdAt) {
  public static TestCaseExecutionDto from(TestCaseExecution exec, String testCaseTitle) {
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
        exec.getCreatedAt() != null ? exec.getCreatedAt().toString() : null);
  }
}
