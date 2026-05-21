package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ApplySuggestionRequest(
        @NotBlank String analysisId,
        String title,
        String description,
        String expectedResult,
        List<CreateTestCaseRequest.StepRequest> steps
) {}
