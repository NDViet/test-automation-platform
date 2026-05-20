package com.platform.common.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Platform-wide object storage abstraction.
 * Implementations: S3CompatibleBlobStore (MinIO / AWS S3 / GCS XML API),
 *                  FilesystemBlobStore (local dev, no containers required).
 *
 * All writes are content-addressed: SHA-256(content) → key.
 * Storing the same bytes twice returns the same BlobRef and skips the upload.
 */
public interface BlobStore {

    /**
     * Store UTF-8 text and return a reference.
     * Idempotent: same content → same BlobRef returned, no second write.
     */
    BlobRef storeText(String bucket, String content, String contentType);

    /**
     * Store raw bytes and return a reference.
     * Idempotent: same bytes → same BlobRef returned, no second write.
     */
    BlobRef storeBytes(String bucket, byte[] content, String contentType);

    /** Fetch stored text content. Empty if the object has expired or been deleted. */
    Optional<String> fetchText(BlobRef ref);

    /** Fetch raw bytes. Empty if the object has expired or been deleted. */
    Optional<byte[]> fetchBytes(BlobRef ref);

    /** True if the object currently exists (not expired or deleted). */
    boolean exists(BlobRef ref);

    /** Delete a single object. No-op if already absent. */
    void delete(BlobRef ref);

    /**
     * Generate a pre-signed URL valid for {@code ttl} that allows direct download
     * without platform credentials. Used by the portal for artifact download links.
     * Throws UnsupportedOperationException for FilesystemBlobStore.
     */
    URI presignUrl(BlobRef ref, Duration ttl);
}
