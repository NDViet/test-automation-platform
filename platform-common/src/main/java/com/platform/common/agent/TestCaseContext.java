package com.platform.common.agent;

import com.platform.common.model.TestCaseRecord;

import java.util.List;

/**
 * Test case context for the Hub→Node contract.
 * Carries existing TCs with their health status, candidates for reuse,
 * uncovered ACs that need new tests, and a human-authored style reference.
 */
public record TestCaseContext(
        List<TestCaseRecord> existing,          // TCs already linked to this requirement
        List<TestCaseRecord> candidates,        // TCs from related requirements for potential reuse
        List<String> uncoveredAcRefs,           // AC refs with zero test coverage
        String styleReferenceTestCaseId,        // optional: example TC for style/format guidance
        int totalActiveTests,
        int totalNeedingUpdate,
        int totalObsolete
) {
    public TestCaseContext {
        existing = existing == null ? List.of() : List.copyOf(existing);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        uncoveredAcRefs = uncoveredAcRefs == null ? List.of() : List.copyOf(uncoveredAcRefs);
    }

    public boolean hasCoverageGaps() { return !uncoveredAcRefs.isEmpty(); }

    public boolean hasObsolete() { return totalObsolete > 0; }
}
