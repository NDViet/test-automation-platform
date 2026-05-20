package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestCaseStep;

public record TestCaseStepDto(
        String id,
        int stepNumber,
        String action,
        String expectedResult,
        String notes
) {
    public static TestCaseStepDto from(TestCaseStep s) {
        return new TestCaseStepDto(
                s.getId() != null ? s.getId().toString() : null,
                s.getStepNumber(),
                s.getAction(),
                s.getExpectedResult(),
                s.getNotes()
        );
    }
}
