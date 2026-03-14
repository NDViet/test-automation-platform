package com.platform.common.dto;

import java.util.List;

/**
 * A single named step within a test case.
 * Steps are captured by the platform-testframework and flow through to the AI
 * classifier and dashboards, giving detailed per-action debugging context.
 */
public record TestStepDto(
        String name,
        String status,        // PASSED | FAILED | BROKEN | SKIPPED
        long durationMs,
        String log,           // captured log lines emitted during this step
        String errorMessage,  // set only when status != PASSED
        List<TestStepDto> steps  // nested steps (e.g. composite / keyword-driven)
) {
    public TestStepDto {
        if (steps == null) steps = List.of();
    }
}
