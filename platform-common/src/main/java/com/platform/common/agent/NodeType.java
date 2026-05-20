package com.platform.common.agent;

/** Specialised node types in the Agent Grid. Each maps to a K8s Deployment. */
public enum NodeType {
    REQUIREMENT,    // Reads external requirements, extracts structured ACs
    TEST_GEN,       // Generates JUnit5 / Playwright / Cucumber test code
    TEST_GENERATION, // Generates manual test cases from requirements (TCM flow)
    AUTOMATION_GEN,  // Generates automated test code from a manual test case and opens a PR
    ANALYSIS,       // PR diff analysis, coverage gap detection
    HEALING,        // Diagnoses TEST_DEFECT failures, proposes minimal fix
    INSIGHT,        // Aggregates trends and produces Slack / portal digest
    EXECUTION       // Triggers CI runs, no LLM required
}
