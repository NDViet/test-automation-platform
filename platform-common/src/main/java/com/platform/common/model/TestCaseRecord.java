package com.platform.common.model;

import com.platform.common.integration.IntegrationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform-canonical test case — manual or agent-generated. Corresponds to a row in {@code
 * platform_test_cases}.
 */
public record TestCaseRecord(
    UUID id,
    String externalId,
    IntegrationType source,
    String title,
    String description,
    List<String> acRefs, // which requirement ACs this TC validates
    CoverageStatus status,
    CreatedBy createdBy,
    UUID agentSessionId, // non-null when status = AGENT_GENERATED
    String lastResult, // PASS | FAIL | BLOCKED | SKIPPED | NOT_RUN
    Instant lastExecutedAt,
    boolean hasAutomation // true if at least one AutomatedTestRef links to this TC
    ) {
  public TestCaseRecord {
    acRefs = acRefs == null ? List.of() : acRefs;
  }

  public enum CoverageStatus {
    ACTIVE,
    NEEDS_UPDATE, // ACs changed — agent should patch assertions
    OBSOLETE, // no longer tied to any active requirement
    ARCHIVED
  }

  public enum CreatedBy {
    AGENT,
    HUMAN
  }
}
