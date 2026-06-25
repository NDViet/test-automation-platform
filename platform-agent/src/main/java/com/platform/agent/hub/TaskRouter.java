package com.platform.agent.hub;

import com.platform.common.agent.AgentTaskType;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.NodeCapabilities;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hub component that selects which registered Node should handle a task. Considers NodeCapabilities
 * (task types, tools, LLM tier, concurrency headroom).
 */
public interface TaskRouter {

  /**
   * Route a single task to the best available node. Returns empty when no node can handle the task
   * right now (caller must retry or queue).
   */
  Optional<NodeCapabilities> route(AgentTaskType taskType, ContextBundle bundle);

  /**
   * Route a full workflow — returns an ordered list of (taskType → nodeId) assignments. Tasks in
   * parallel can share a position in the plan; sequential tasks must be ordered.
   */
  List<TaskAssignment> plan(ContextBundle bundle);

  /** A single resolved assignment: which node executes which task. */
  record TaskAssignment(UUID nodeId, AgentTaskType taskType, int sequenceOrder) {}
}
