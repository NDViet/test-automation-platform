package com.platform.common.agent;

/** Strategy selected by CheckpointService when resuming a paused session. */
public enum ResumeStrategy {
  /** Cache hit on context_bundle — 10% token cost. Valid within 5-min TTL. */
  PROMPT_CACHE,
  /** Compressed step summaries replace raw turns — ~75% token savings vs full replay. */
  COMPRESSED,
  /** Structured handoff document replaces all history — used beyond 24h. */
  HANDOFF
}
