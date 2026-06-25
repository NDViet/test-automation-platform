package com.platform.common.integration;

import java.time.Instant;
import java.util.List;

/**
 * Result returned by {@link IntegrationAdapter#syncInbound}. Contains the cursor for the next
 * incremental sync.
 */
public record SyncResult(
    int synced,
    int skipped,
    int failed,
    List<String> errors, // error summaries for the sync log
    SyncCursor nextCursor, // null = sync complete (no more pages)
    Instant completedAt) {
  public SyncResult {
    errors = errors == null ? List.of() : errors;
  }

  public static SyncResult of(int synced, SyncCursor nextCursor) {
    return new SyncResult(synced, 0, 0, List.of(), nextCursor, Instant.now());
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }
}
