package com.platform.common.storage;

/**
 * Canonical bucket names used across all platform modules. Bucket lifecycle policies are configured
 * on startup by BlobStoreBuckets (in platform-storage):
 *
 * <p>ARTIFACTS — permanent (no expiry) KNOWLEDGE — permanent (approved RAG training examples)
 * CHECKPOINTS— auto-expire after 30 days (agent conversation state) DIFFS — auto-expire after 7
 * days (PR diffs, ephemeral large inputs)
 */
public final class BlobStoreBuckets {

  private BlobStoreBuckets() {}

  /** Agent-produced artifacts awaiting or after human review. */
  public static final String ARTIFACTS = "platform-artifacts";

  /** Approved agent outputs used as RAG few-shot examples. */
  public static final String KNOWLEDGE = "platform-knowledge";

  /** Agent conversation state for session resume (COMPRESSED / HANDOFF strategies). */
  public static final String CHECKPOINTS = "platform-checkpoints";

  /** PR diffs and other large ephemeral inputs fetched during a session. */
  public static final String DIFFS = "platform-diffs";

  /** Playwright trace ZIPs uploaded by the streaming reporter after test failures. */
  public static final String TRACES = "platform-traces";
}
