package com.platform.analytics.impact;

/**
 * Risk level when running only the impact-selected subset of tests.
 *
 * <ul>
 *   <li>{@code LOW} — all changed classes are covered by the selected tests.
 *   <li>{@code MEDIUM} — ≥80% of changed classes are covered.
 *   <li>{@code HIGH} — 50–79% of changed classes are covered.
 *   <li>{@code CRITICAL} — <50% of changed classes are covered; recommend running the full suite.
 * </ul>
 */
public enum RiskLevel {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
