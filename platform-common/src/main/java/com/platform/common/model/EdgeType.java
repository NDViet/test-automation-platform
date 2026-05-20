package com.platform.common.model;

/** Typed relationships between nodes in the five-tier traceability graph. */
public enum EdgeType {

    // Within REQUIREMENT tier
    PARENT_OF,      // Epic → Story, Story → Sub-task
    LINKED_TO,      // Generic cross-requirement link (see LinkSubtype for specifics)

    // Cross-tier edges (canonical traceability chain)
    COVERED_BY,     // REQUIREMENT → TEST_CASE: this requirement is tested by this TC
    AUTOMATED_BY,   // TEST_CASE → AUTOMATED_TEST: the manual TC has a code counterpart
    RAN_IN,         // AUTOMATED_TEST → EXECUTION: this test ran in this CI run
    IMPLEMENTS,     // CODE_CHANGE → REQUIREMENT: this PR implements this requirement
    MONITORED_BY,   // REQUIREMENT → MONITOR: this feature has a production monitor

    // Defect linkage
    FOUND_BY,       // DEFECT/REQUIREMENT → TEST_CASE: which test surfaced this bug
    CAUSED_BY,      // DEFECT → CODE_CHANGE: which commit introduced the regression

    // Release scoping
    SCOPED_TO,      // REQUIREMENT → RELEASE: in scope for this release
    PLANNED_IN,     // TEST_CASE → TEST_PLAN: part of this test plan
    EXECUTED_IN     // TEST_CASE → TEST_PLAN: ran with a result (see metadata for outcome)
}
