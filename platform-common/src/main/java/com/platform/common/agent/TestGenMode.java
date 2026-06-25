package com.platform.common.agent;

/**
 * Instructs TestGenNode on the generation strategy. Resolved by ContextAssembler before the session
 * is dispatched.
 */
public enum TestGenMode {
  /** No existing TCs — generate from scratch for all ACs. */
  CREATE_ALL,
  /** Some TCs exist and are ACTIVE — only generate for uncovered new ACs. */
  CREATE_FOR_NEW_ACS,
  /** Some TCs are NEEDS_UPDATE — patch specific assertions; list in TestCaseContext. */
  UPDATE_CHANGED,
  /** Candidate TCs from a related/cloned requirement cover ≥ 80% of ACs — just link them. */
  REUSE_FROM_RELATED,
  /** All ACs are covered and current — nothing to generate. */
  NO_ACTION
}
