package com.platform.analytics.impact;

/**
 * Risk level when running only the impact-selected subset of tests.
 *
 * <ul>
 *   <li>{@code LOW} — all changed classes are covered by the selected tests.</li>
 *   <li>{@code MEDIUM} — ≥80% of changed classes are covered.</li>
 *   <li>{@code HIGH} — 50–79% of changed classes are covered.</li>
 *   <li>{@code CRITICAL} — <50% of changed classes are covered; recommend running the full suite.</li>
 * </ul>
 */
public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}
