package com.platform.common.agent;

import com.platform.common.model.AutomatedTestRef;

import java.util.List;

/**
 * Automated test context for the Hub→Node contract.
 * Carries existing test code references, detected coverage gaps, and repo targeting.
 */
public record AutomatedTestContext(
        List<AutomatedTestRef> existing,        // known automated tests for this scope
        List<String> coverageGapAcRefs,         // AC refs with no automated test
        List<String> failingTestIds,            // IDs of tests currently failing (healing context)
        String repoUrl,
        String branch,
        String testDirectory,                   // e.g. src/test/java/com/example/
        String framework                        // "junit5", "playwright", "cucumber"
) {
    public AutomatedTestContext {
        existing = existing == null ? List.of() : List.copyOf(existing);
        coverageGapAcRefs = coverageGapAcRefs == null ? List.of() : List.copyOf(coverageGapAcRefs);
        failingTestIds = failingTestIds == null ? List.of() : List.copyOf(failingTestIds);
    }

    public boolean needsNewAutomation() { return !coverageGapAcRefs.isEmpty(); }

    public boolean hasFailuresForHealing() { return !failingTestIds.isEmpty(); }
}
