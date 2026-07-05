package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestCaseRevision;

/**
 * One version-history entry for a test case. {@code snapshot} is the JSON state at that version.
 */
public record TestCaseRevisionDto(
    String id,
    int version,
    String snapshot,
    String changedBy,
    String changeKind,
    String changedAt) {

  public static TestCaseRevisionDto from(TestCaseRevision r) {
    return new TestCaseRevisionDto(
        r.getId() != null ? r.getId().toString() : null,
        r.getVersion(),
        r.getSnapshot(),
        r.getChangedBy(),
        r.getChangeKind(),
        r.getChangedAt() != null ? r.getChangedAt().toString() : null);
  }
}
