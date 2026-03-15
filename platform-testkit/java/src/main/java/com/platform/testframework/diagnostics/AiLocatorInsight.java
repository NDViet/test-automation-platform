package com.platform.testframework.diagnostics;

import java.util.List;

/**
 * @deprecated AI analysis is now performed server-side by the platform backend.
 * This record is kept only for binary compatibility — it is no longer produced
 * or consumed by the testkit itself.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public record AiLocatorInsight(
        String analysis,
        List<String> alternativeSelectors,
        String suggestedFix) {}
