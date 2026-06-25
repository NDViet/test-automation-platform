package com.platform.agent.hub;

import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.TriggerRef;
import java.util.UUID;

/**
 * Hub component that builds a ContextBundle from a trigger event. Queries platform-core for
 * requirement/test/execution/monitor data, resolves credentials from the secrets store, and
 * determines TestGenMode.
 */
public interface ContextAssembler {

  /**
   * Build a full ContextBundle for a new workflow initiated by the given trigger. All five-tier
   * context sections are populated based on what's relevant to the trigger.
   */
  ContextBundle assemble(UUID workflowId, UUID projectId, TriggerRef trigger);

  /**
   * Re-hydrate a ContextBundle from a stored checkpoint for session resume. Refreshes live
   * execution/monitor context; preserves requirement and test case context.
   */
  ContextBundle resume(UUID workflowId, String checkpointId);
}
