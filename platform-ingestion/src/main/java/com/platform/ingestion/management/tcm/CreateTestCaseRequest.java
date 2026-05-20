package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateTestCaseRequest(
        @NotBlank String title,
        String description,
        String preconditions,
        String expectedResult,
        String priority,
        String suiteId,
        String sourceRequirementId,
        List<String> acRefs,
        List<StepRequest> steps
) {
    public record StepRequest(
            String action,
            String expectedResult,
            String notes
    ) {}
}
