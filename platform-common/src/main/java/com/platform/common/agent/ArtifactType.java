package com.platform.common.agent;

/** Types of artifacts that nodes can produce in a session. */
public enum ArtifactType {
  PR_OPENED,
  COMMIT_PUSHED,
  TEST_CODE_FILE, // generated automated test code
  TEST_CASE_MANUAL, // generated manual test case (pushed to TestRail/Xray/portal)
  HEAL_FIX, // patch for a failing test
  PR_REVIEW_COMMENT,
  TICKET_CREATED,
  TICKET_UPDATED,
  TICKET_CLOSED,
  COVERAGE_REPORT,
  SPRINT_DIGEST,
  SLACK_MESSAGE,
  RELEASE_TEST_PLAN, // test plan derived from release scope / DERIVE_TEST_PLAN task
  FAILURE_ANALYSIS_REPORT, // structured root-cause analysis from CLASSIFY_FAILURE
  OBSOLESCENCE_REPORT, // list of obsolete tests from DETECT_OBSOLETE_TESTS
  FLAKINESS_REPORT, // per-test flakiness detail from INVESTIGATE_FLAKINESS
  REQUIREMENT_EXTRACTED // requirement record synced from an external source (Jira, Linear)
}
