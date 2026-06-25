package com.platform.core.service;

/** How a test case's properties expand into per-combination executions. */
public enum MatrixType {
  /** Full Cartesian product of all property values. */
  FULL,
  /** Reduced set where every pair of values across any two properties appears at least once. */
  PAIRWISE
}
