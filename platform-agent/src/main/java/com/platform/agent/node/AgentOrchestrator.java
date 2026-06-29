package com.platform.agent.node;

import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.NodeResult;

/**
 * Drives a Node through a multi-step Claude conversation. Manages the tool-use loop: send message →
 * receive tool_use → execute tool → send tool_result → repeat. Calls CheckpointService to persist
 * intermediate state between turns.
 */
public interface AgentOrchestrator {

  /**
   * Run the Claude conversation loop for the given bundle until the node reaches a terminal state
   * (completed, awaiting_review, or failed).
   */
  NodeResult run(ContextBundle bundle, AgentNode node);

  /**
   * Resume a paused conversation from a stored checkpoint. Rehydrates the message history, appends
   * {@code nextUserMessage} (e.g. the user's clarification answers) as the next user turn, and
   * resumes the tool-use loop.
   */
  NodeResult resume(
      ContextBundle bundle, String checkpointId, AgentNode node, String nextUserMessage);
}
