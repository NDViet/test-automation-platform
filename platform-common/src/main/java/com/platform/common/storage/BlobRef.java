package com.platform.common.storage;

/**
 * Immutable reference to a blob stored in the platform object store. Carries enough information to
 * fetch the content without a database lookup. Safe to serialize into JSONB columns or Kafka
 * messages.
 *
 * <p>Keys are content-addressed (SHA-256), so the same bytes always produce the same BlobRef —
 * deduplication is automatic.
 */
public record BlobRef(
    String bucket, // BlobStoreBuckets constant
    String key, // "{hash[0:2]}/{hash}" — content-addressed
    String contentHash, // SHA-256 hex — also used for integrity verification
    String contentType, // MIME type: "application/json", "text/x-patch", etc.
    long sizeBytes) {
  public static final String TYPE_JSON = "application/json";
  public static final String TYPE_TEXT = "text/plain; charset=utf-8";
  public static final String TYPE_PATCH = "text/x-patch";
  public static final String TYPE_JAVA = "text/x-java-source";

  /** Derive the key from a content hash. */
  public static String keyFor(String contentHash) {
    return contentHash.substring(0, 2) + "/" + contentHash;
  }
}
