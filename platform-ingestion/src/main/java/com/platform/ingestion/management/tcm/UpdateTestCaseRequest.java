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
    /**
     * Full replacement of the test case's linked requirement set (optional, many). {@code null} =
     * leave the links unchanged; {@code []} = unlink everything (linking is optional). A non-null
     * list replaces the whole set.
     */
    List<String> linkedRequirementIds,
    List<String> acRefs,
    List<CreateTestCaseRequest.StepRequest> steps) {}
