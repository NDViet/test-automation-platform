package com.platform.common.agent;

/** LLM capability tier for a node. Drives model selection in AgentRunner. */
public enum LlmTier {
  /** claude-sonnet-4-6 — fast, cost-efficient for analysis and structured extraction. */
  STANDARD,
  /** claude-opus-4-7 — deep reasoning for test code generation and complex healing. */
  COMPLEX,
  /** No LLM — pure orchestration (e.g., ExecutionNode triggering CI). */
  NONE
}
