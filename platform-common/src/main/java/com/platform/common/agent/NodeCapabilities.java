package com.platform.common.agent;

import java.util.Set;
import java.util.UUID;

/**
 * Capabilities advertised by a node on registration.
 * The Hub's CapabilityMatcher uses this to route sessions.
 */
public record NodeCapabilities(
        UUID nodeId,
        NodeType nodeType,
        Set<AgentTaskType> supportedTaskTypes,
        Set<String> tools,          // tool IDs: "GITHUB_READ", "JIRA_WRITE", "PLATFORM_API", etc.
        LlmTier llmTier,
        int maxConcurrentSessions,
        String endpoint,            // http://node-host:port — Hub calls /node/sessions on this
        String version
) {
    public boolean supports(AgentTaskType taskType) {
        return supportedTaskTypes.contains(taskType);
    }

    public boolean hasTool(String toolId) {
        return tools.contains(toolId);
    }
}
