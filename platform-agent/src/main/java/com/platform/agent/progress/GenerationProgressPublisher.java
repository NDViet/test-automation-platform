package com.platform.agent.progress;

import java.util.UUID;

/**
 * Publishes live generation progress for a workflow so the portal can relay it to the browser over
 * WebSocket. The token stream is the liveness signal — as long as deltas keep arriving, the run is
 * demonstrably still transferring content (not hung), which is exactly what a hardcoded wall-clock
 * timeout cannot tell apart.
 */
public interface GenerationProgressPublisher {

  /** A run has begun streaming for {@code workflowId}. */
  void started(UUID workflowId);

  /**
   * A throttled snapshot of the model's output so far. {@code preview} is a bounded tail of the
   * accumulated text (for a live preview); {@code chars} is the full length generated so far.
   */
  void token(UUID workflowId, String preview, int chars);

  /** Streaming for {@code workflowId} has stopped with the given terminal/pause status. */
  void finished(UUID workflowId, String status);
}
