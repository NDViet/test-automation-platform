package com.platform.common.agent;

import com.platform.common.storage.BlobRef;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The primary Hub→Node contract.
 * Assembles all five-tier context sections into one immutable payload.
 * Hub builds this; Nodes receive it and never modify it.
 */
public record ContextBundle(
        // Session identity
        UUID sessionId,
        UUID workflowId,
        UUID projectId,
        String projectSlug,
        List<AgentTaskType> taskTypes,

        // What fired this session
        TriggerRef trigger,

        // Five-tier context sections (null when tier is out of scope)
        RequirementContext requirementContext,
        TestCaseContext testCaseContext,
        AutomatedTestContext automatedTestContext,
        ExecutionContext executionContext,
        MonitorContext monitorContext,

        // Cross-cutting
        SessionCredentials credentials,
        OutboundTargets outboundTargets,

        // Large inputs offloaded to blob store (null when not applicable to this task)
        BlobRef prDiff,             // DIFFS bucket; used by ANALYZE_PR_DIFF tasks
        String releaseVersion,      // tag/milestone label; used by DERIVE_TEST_PLAN

        // Session control
        ResumeStrategy resumeStrategy,
        String checkpointId,        // non-null when resuming from checkpoint
        LlmTier llmTier,
        Instant assembledAt
) {
    public ContextBundle {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }

    public boolean isResume() { return checkpointId != null; }

    public boolean hasRequirements() { return requirementContext != null; }

    public boolean hasExecutionHistory() { return executionContext != null; }

    public boolean hasActiveIncidents() {
        return monitorContext != null && monitorContext.hasActiveIncidents();
    }

    public boolean hasPrDiff() { return prDiff != null; }
}
