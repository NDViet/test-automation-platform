package com.platform.common.agent;

/** Granular task types that nodes can execute within a workflow session. */
public enum AgentTaskType {

  // Requirement flow
  EXTRACT_ACCEPTANCE_CRITERIA,
  GENERATE_MANUAL_TEST_CASES,
  GENERATE_AUTOMATED_TESTS,
  UPDATE_TEST_CASES, // patch existing TCs when ACs change
  REUSE_TEST_CASES, // link existing TCs from related requirements

  // PR review flow
  ANALYZE_PR_DIFF,
  DETECT_COVERAGE_GAPS,
  FILL_COVERAGE_GAPS,
  POST_PR_REVIEW_COMMENT,

  // Healing flow
  CLASSIFY_FAILURE, // typically done by platform-ai, but node can re-classify
  PROPOSE_HEAL_FIX,
  COMMIT_HEAL_FIX,

  // Insight flow
  GENERATE_NIGHTLY_DIGEST,
  GENERATE_RELEASE_REPORT,
  INVESTIGATE_FLAKINESS,

  // Execution flow
  TRIGGER_CI_RUN,
  DERIVE_TEST_PLAN, // compute test plan from release scope
  DETECT_OBSOLETE_TESTS,

  // Integration / knowledge sync flow
  SYNC_FROM_EXTERNAL, // pull requirements/stories from Jira, Linear, etc.
  DETECT_DUPLICATE_REQUIREMENTS, // find semantically similar requirements across projects

  // TCM (Test Case Management) generation flow
  GENERATE_TEST_CASES, // generate manual test cases from requirements
  GENERATE_AUTOMATION_CODE // generate automated test code from an approved manual test case
}
