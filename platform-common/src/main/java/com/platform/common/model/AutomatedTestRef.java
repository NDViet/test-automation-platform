package com.platform.common.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight reference to an automated test method.
 * Full execution history is available via the platform-analytics API.
 */
public record AutomatedTestRef(
        UUID id,
        String className,
        String methodName,
        String filePath,
        String framework,        // JUNIT5 | TESTNG | PLAYWRIGHT | CUCUMBER
        String lastResult,       // PASS | FAIL | BROKEN | SKIPPED
        Instant lastModifiedAt
) {}
