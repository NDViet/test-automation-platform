package com.platform.agent.node;

import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.ResumeStrategy;
import com.platform.common.storage.BlobRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persists and restores conversation checkpoints for session resume.
 *
 * <p>Strategy semantics: PROMPT_CACHE — conversation lives in node memory; full messages resent on
 * every Claude API call with cache_control markers (billing saved, not round-trips). Checkpoint
 * stores the messages array in blob store (CHECKPOINTS bucket) so the node can recover if it
 * restarts within the 5-min cache window.
 *
 * <p>COMPRESSED — old turns are summarized with haiku-4-5; recent N turns kept verbatim. Redis
 * stores a pointer to the blobs. Survives node restart, TTL 24h.
 *
 * <p>HANDOFF — domain-structured state extracted from the conversation (which requirements were
 * analyzed, which test cases were drafted, which decisions were made). Stored in PostgreSQL
 * (agent_checkpoints table). No TTL. Used for checkpoints older than 24h or when crossing node
 * process boundaries.
 */
public interface CheckpointService {

  /**
   * Save a checkpoint. Returns the checkpoint ID for later resume. Large content (messages,
   * structured state) is offloaded to the blob store.
   */
  String save(ContextBundle bundle, ConversationState state, ResumeStrategy strategy);

  /**
   * Load a previously saved checkpoint. Returns empty if expired, deleted, or blob content
   * unreachable.
   */
  Optional<ConversationState> load(String checkpointId);

  /** Extend the TTL on a COMPRESSED checkpoint (used when reviewer clicks "Defer"). */
  void extend(String checkpointId, java.time.Duration extension);

  /** Delete after successful session completion. */
  void delete(String checkpointId);

  /**
   * Snapshot of a Node's in-progress conversation.
   *
   * <p>messagesBlob — full Anthropic messages array JSON (all strategies). Stored in CHECKPOINTS
   * bucket; auto-expires per bucket lifecycle. compressedSummary— haiku-compressed summary injected
   * as context prefix (COMPRESSED only; null otherwise). Small enough to stay in-line. handoffBlob
   * — domain-structured state JSON (HANDOFF only; null otherwise). Stored in CHECKPOINTS bucket (no
   * expiry for HANDOFF strategy). cacheTurnIndices — which message indices had cache_control:
   * ephemeral set. Restored on resume so billing cache markers are preserved.
   */
  record ConversationState(
      String sessionId,
      BlobRef messagesBlob,
      String compressedSummary,
      BlobRef handoffBlob,
      List<Integer> cacheTurnIndices,
      ResumeStrategy strategy,
      Instant savedAt) {
    public ConversationState {
      cacheTurnIndices = cacheTurnIndices == null ? List.of() : List.copyOf(cacheTurnIndices);
    }
  }
}
