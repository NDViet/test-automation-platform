package com.platform.common.agent;

import com.platform.common.storage.BlobRef;
import java.time.Instant;
import java.util.UUID;

/**
 * Reference to a single artifact produced by a node during a session. Small artifacts (summaries,
 * ticket keys) carry only metadata here. Large artifacts (generated code, diffs, test plans) are
 * stored in the blob store; {@code blob} carries the reference, {@code contentHash} is its SHA-256.
 */
public record ArtifactRef(
    UUID artifactId,
    ArtifactType type,
    String destination, // repo URL, JIRA project key, Slack channel
    String destinationRef, // PR URL, ticket ID, Slack message ts, file path
    String contentHash, // SHA-256 of content; matches blob.contentHash when blob != null
    BlobRef blob, // null for small artifacts (< 64 KB); non-null for large content
    long sizeBytes, // 0 when blob is null
    Instant producedAt) {
  /** Convenience factory for small artifacts with no blob storage. */
  public static ArtifactRef small(
      UUID id, ArtifactType type, String destination, String destinationRef, String contentHash) {
    return new ArtifactRef(
        id, type, destination, destinationRef, contentHash, null, 0, Instant.now());
  }

  /** Convenience factory for large artifacts backed by the blob store. */
  public static ArtifactRef large(
      UUID id, ArtifactType type, String destination, String destinationRef, BlobRef blob) {
    return new ArtifactRef(
        id,
        type,
        destination,
        destinationRef,
        blob.contentHash(),
        blob,
        blob.sizeBytes(),
        Instant.now());
  }

  public boolean hasBlob() {
    return blob != null;
  }
}
