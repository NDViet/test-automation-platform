package com.platform.storage;

import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * BlobStore implementation backed by any S3-compatible object store. Tested with MinIO
 * (self-hosted) and AWS S3. Works with GCS via its XML API interoperability endpoint.
 *
 * <p>Storage is content-addressed: SHA-256(content) determines the key, so identical content is
 * stored exactly once across the entire platform.
 */
public class S3CompatibleBlobStore implements BlobStore {

  private static final Logger log = LoggerFactory.getLogger(S3CompatibleBlobStore.class);

  private final S3Client s3;
  private final S3Presigner presigner;

  public S3CompatibleBlobStore(S3Client s3, S3Presigner presigner) {
    this.s3 = s3;
    this.presigner = presigner;
  }

  @Override
  public BlobRef storeText(String bucket, String content, String contentType) {
    return storeBytes(bucket, content.getBytes(StandardCharsets.UTF_8), contentType);
  }

  @Override
  public BlobRef storeBytes(String bucket, byte[] content, String contentType) {
    String hash = sha256Hex(content);
    String key = BlobRef.keyFor(hash);

    if (!objectExists(bucket, key)) {
      s3.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(contentType)
              .contentLength((long) content.length)
              .build(),
          RequestBody.fromBytes(content));
      log.debug("blob stored: {}/{} ({} bytes)", bucket, key, content.length);
    } else {
      log.debug("blob deduplicated: {}/{}", bucket, key);
    }

    return new BlobRef(bucket, key, hash, contentType, content.length);
  }

  @Override
  public Optional<String> fetchText(BlobRef ref) {
    return fetchBytes(ref).map(b -> new String(b, StandardCharsets.UTF_8));
  }

  @Override
  public Optional<byte[]> fetchBytes(BlobRef ref) {
    try {
      ResponseBytes<GetObjectResponse> response =
          s3.getObjectAsBytes(
              GetObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
      return Optional.of(response.asByteArray());
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean exists(BlobRef ref) {
    return objectExists(ref.bucket(), ref.key());
  }

  @Override
  public void delete(BlobRef ref) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
  }

  @Override
  public URI presignUrl(BlobRef ref, Duration ttl) {
    var presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(r -> r.bucket(ref.bucket()).key(ref.key()))
            .build();
    try {
      return presigner.presignGetObject(presignRequest).url().toURI();
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException("presigned URL is not a valid URI", e);
    }
  }

  // -------------------------------------------------------------------------

  private boolean objectExists(String bucket, String key) {
    try {
      s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
      return true;
    } catch (S3Exception e) {
      return false;
    }
  }

  private static String sha256Hex(byte[] data) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
