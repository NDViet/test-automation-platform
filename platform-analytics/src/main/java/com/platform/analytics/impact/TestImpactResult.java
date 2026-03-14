package com.platform.analytics.impact;

import java.util.List;

/**
 * Response from the Test Impact Analysis endpoint.
 *
 * @param recommendedTests        Test IDs (class#method) that cover the changed files.
 * @param totalTests              Total tests in the project (from coverage mapping).
 * @param selectedTests           Number of recommended tests.
 * @param estimatedReduction      Estimated CI time reduction (e.g. "74%").
 * @param riskLevel               Confidence that the selected set is sufficient.
 * @param uncoveredChangedClasses Changed classes with NO coverage mappings (risk).
 * @param allChangedClasses       All class names derived from the changed file list.
 * @param junitFilter             Maven Surefire {@code -Dtest=} filter string.
 * @param mavenFilter             Alias for {@code junitFilter}.
 * @param gradleFilter            Gradle {@code --tests} filter string.
 */
public record TestImpactResult(
        List<String> recommendedTests,
        int totalTests,
        int selectedTests,
        String estimatedReduction,
        RiskLevel riskLevel,
        List<String> uncoveredChangedClasses,
        List<String> allChangedClasses,
        String junitFilter,
        String mavenFilter,
        String gradleFilter
) {
    /** Used when there are no changed files — no filtering needed. */
    static TestImpactResult noChanges(int totalTests) {
        return new TestImpactResult(
                List.of(), totalTests, totalTests, "0%",
                RiskLevel.LOW, List.of(), List.of(), "", "", "");
    }
}
