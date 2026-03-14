package com.platform.common.dto;

import java.util.List;

/**
 * Standalone coverage manifest — submitted separately from a test run.
 * Used by teams that want to register their test-to-class mappings via JaCoCo
 * post-processing or a custom script, rather than @AffectedBy annotations.
 *
 * <pre>{@code
 * POST /api/v1/coverage
 * {
 *   "projectId": "proj-checkout",
 *   "mappings": [
 *     { "testId": "com.example.CheckoutTest#verifyTotal",
 *       "coveredClasses": ["com.example.PaymentService", "com.example.CartService"] }
 *   ]
 * }
 * }</pre>
 */
public record CoverageManifest(
        String projectId,
        List<Entry> mappings
) {
    public record Entry(
            String testId,
            List<String> coveredClasses
    ) {}
}
