package com.platform.common.integration;

import java.time.Instant;
import java.util.Map;

/**
 * Opaque pagination state carried between incremental sync calls. Adapters store whatever they need
 * (page token, offset, last-updated-at).
 */
public record SyncCursor(
    Instant lastSyncAt,
    String pageToken, // adapter-specific pagination token
    Map<String, String> state // arbitrary adapter-specific state
    ) {
  public SyncCursor {
    state = state == null ? Map.of() : state;
  }

  public static SyncCursor initial() {
    return new SyncCursor(Instant.EPOCH, null, Map.of());
  }

  public SyncCursor advance(Instant newLastSyncAt, String nextPageToken) {
    return new SyncCursor(newLastSyncAt, nextPageToken, state);
  }
}
