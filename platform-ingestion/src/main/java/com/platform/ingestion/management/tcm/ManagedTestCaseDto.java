package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseStep;

import java.util.List;

public record ManagedTestCaseDto(
        String id,
        String projectId,
        String suiteId,
        String externalId,
        String title,
        String description,
        String preconditions,
        String expectedResult,
        String priority,
        String status,
        String coverageStatus,
        String createdBy,
        String agentSessionId,
        String sourceRequirementId,
        List<String> acRefs,
        String automationStatus,
        String automationPrUrl,
        boolean hasAutomation,
        String lastResult,
        String lastExecutedAt,
        List<TestCaseStepDto> steps,
        String createdAt,
        String updatedAt
) {
    public static ManagedTestCaseDto from(PlatformTestCase tc, List<TestCaseStep> steps) {
        return new ManagedTestCaseDto(
                tc.getId() != null ? tc.getId().toString() : null,
                tc.getProjectId() != null ? tc.getProjectId().toString() : null,
                tc.getSuiteId() != null ? tc.getSuiteId().toString() : null,
                tc.getExternalId(),
                tc.getTitle(),
                tc.getDescription(),
                tc.getPreconditions(),
                tc.getExpectedResult(),
                tc.getPriority(),
                tc.getStatus(),
                tc.getCoverageStatus(),
                tc.getCreatedBy(),
                tc.getAgentSessionId() != null ? tc.getAgentSessionId().toString() : null,
                tc.getSourceRequirementId() != null ? tc.getSourceRequirementId().toString() : null,
                tc.getAcRefs() != null ? tc.getAcRefs() : List.of(),
                tc.getAutomationStatus(),
                tc.getAutomationPrUrl(),
                tc.isHasAutomation(),
                tc.getLastResult(),
                tc.getLastExecutedAt() != null ? tc.getLastExecutedAt().toString() : null,
                steps != null ? steps.stream().map(TestCaseStepDto::from).toList() : List.of(),
                tc.getCreatedAt() != null ? tc.getCreatedAt().toString() : null,
                tc.getUpdatedAt() != null ? tc.getUpdatedAt().toString() : null
        );
    }
}
