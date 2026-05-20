package com.platform.agent.hub.impl;

import com.platform.common.agent.*;
import com.platform.agent.hub.NodeRegistry;
import com.platform.agent.hub.TaskRouter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Routes tasks to nodes based on capability matching.
 * Prefers nodes with lower current session load (maxConcurrentSessions is a hint).
 */
@Component
public class CapabilityTaskRouter implements TaskRouter {

    private final NodeRegistry registry;

    public CapabilityTaskRouter(NodeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Optional<NodeCapabilities> route(AgentTaskType taskType, ContextBundle bundle) {
        LlmTier required = bundle.llmTier() != null ? bundle.llmTier() : LlmTier.STANDARD;
        return registry.all().stream()
                .filter(n -> n.supports(taskType))
                .filter(n -> n.llmTier().ordinal() >= required.ordinal())
                .min(Comparator.comparingInt(NodeCapabilities::maxConcurrentSessions).reversed());
    }

    @Override
    public List<TaskAssignment> plan(ContextBundle bundle) {
        List<TaskAssignment> assignments = new ArrayList<>();
        int order = 0;
        for (AgentTaskType taskType : bundle.taskTypes()) {
            Optional<NodeCapabilities> node = route(taskType, bundle);
            if (node.isPresent()) {
                assignments.add(new TaskAssignment(node.get().nodeId(), taskType, order++));
            }
        }
        return assignments;
    }
}
