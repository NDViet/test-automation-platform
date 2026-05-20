package com.platform.ingestion.management.tcm;

import java.util.List;

public record UpdateTestCaseRequest(
        String title,
        String description,
        String preconditions,
        String expectedResult,
        String priority,
        String suiteId,
        String sourceRequirementId,
        List<String> acRefs,
        List<CreateTestCaseRequest.StepRequest> steps
) {}
