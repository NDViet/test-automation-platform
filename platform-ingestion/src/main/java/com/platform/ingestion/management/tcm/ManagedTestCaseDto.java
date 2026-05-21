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
        String updatedBy,
        String agentSessionId,
        String sourceRequirementId,
        List<String> acRefs,
        List<String> linkedRequirementIds,
        String automationStatus,
        String automationPrUrl,
        String automationWorkflowId,
        boolean hasAutomation,
        String lastUpdatedByAnalysisId,
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
                tc.getUpdatedBy() != null ? tc.getUpdatedBy() : "HUMAN",
                tc.getAgentSessionId() != null ? tc.getAgentSessionId().toString() : null,
                tc.getSourceRequirementId() != null ? tc.getSourceRequirementId().toString() : null,
                tc.getAcRefs() != null ? tc.getAcRefs() : List.of(),
                tc.getLinkedRequirementIds() != null ? tc.getLinkedRequirementIds() : List.of(),
                tc.getAutomationStatus(),
                tc.getAutomationPrUrl(),
                tc.getAutomationWorkflowId() != null ? tc.getAutomationWorkflowId().toString() : null,
                tc.isHasAutomation(),
                tc.getLastUpdatedByAnalysisId() != null ? tc.getLastUpdatedByAnalysisId().toString() : null,
                tc.getLastResult(),
                tc.getLastExecutedAt() != null ? tc.getLastExecutedAt().toString() : null,
                steps != null ? steps.stream().map(TestCaseStepDto::from).toList() : List.of(),
                tc.getCreatedAt() != null ? tc.getCreatedAt().toString() : null,
                tc.getUpdatedAt() != null ? tc.getUpdatedAt().toString() : null
        );
    }
}
