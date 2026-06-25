package com.platform.analytics.automated;

import java.time.Instant;
import java.util.UUID;

public record RecentRunDto(
    String runId,
    UUID resultId,
    String status,
    Instant runAt,
    Long durationMs,
    String failureMessage,
    String environment,
    String branch,
    boolean hasTrace,
    String browser,
    String specFile,
    boolean hasScreenshot,
    boolean hasVideo) {}
