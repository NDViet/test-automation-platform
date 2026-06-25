package com.platform.common.integration;

public enum SyncDirection {
  /** Pull records from the external system into the platform. */
  INBOUND,
  /** Push platform artifacts (generated tests, fix PRs) out to the external system. */
  OUTBOUND,
  /** Both directions — read requirements in, push test cases and results back. */
  BIDIRECTIONAL
}
