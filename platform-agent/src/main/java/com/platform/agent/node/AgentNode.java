package com.platform.agent.node;

import com.platform.common.agent.*;

/**
 * Stateless task executor contract for all Node types. Each implementation handles a specific
 * AgentTaskType using its LLM + tool set. Nodes are identified by their NodeCapabilities and
 * registered with the Hub on startup.
 */
public interface AgentNode {

  /** The task type this node handles. */
  AgentTaskType taskType();

  /** The node type category this implementation belongs to. */
  NodeType nodeType();

  /**
   * Execute the task described by the ContextBundle. Returns a NodeResult — never throws; errors
   * are captured in NodeResult.status/errorMessage. When human review is needed, returns status
   * AWAITING_REVIEW with a non-null checkpointId.
   */
  NodeResult execute(ContextBundle bundle);

  /**
   * Optional node-specific system prompt. When non-null, replaces the generic orchestrator prompt.
   * Nodes that need precise step-by-step instructions (e.g. AnalysisNode) should override this.
   */
  default String systemPrompt(ContextBundle bundle) {
    return null;
  }

  /**
   * Dispatch a tool call from the Claude tool-use loop. Returns the tool result as a plain string.
   * Return a string starting with "__AWAITING_REVIEW__" to pause and request human review. Default
   * implementation returns an acknowledgement for unknown tools.
   */
  default String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
    return "Tool '" + toolName + "' acknowledged. Input: " + inputJson;
  }

  /** Tool definitions advertised to Claude for this node. */
  default java.util.List<com.anthropic.models.messages.Tool> tools() {
    return java.util.List.of();
  }
}
