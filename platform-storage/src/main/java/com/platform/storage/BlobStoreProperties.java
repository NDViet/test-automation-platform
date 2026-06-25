package com.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * platform.storage.* configuration properties.
 *
 * <p>Minimal docker-compose (MinIO self-hosted): platform.storage.type=minio
 * platform.storage.endpoint=http://minio:9000 platform.storage.access-key=platform
 * platform.storage.secret-key=platform123 platform.storage.region=us-east-1 # arbitrary for MinIO
 * platform.storage.path-style=true # required for MinIO
 *
 * <p>AWS S3: platform.storage.type=s3 platform.storage.region=ap-southeast-1
 * platform.storage.access-key=${AWS_ACCESS_KEY_ID}
 * platform.storage.secret-key=${AWS_SECRET_ACCESS_KEY} platform.storage.path-style=false #
 * virtual-hosted style for S3
 *
 * <p>Local dev (no containers): platform.storage.type=filesystem
 * platform.storage.filesystem-base-path=/tmp/platform-blobs
 */
@ConfigurationProperties(prefix = "platform.storage")
public class BlobStoreProperties {

  private Type type = Type.filesystem;
  private String endpoint;
  private String region = "us-east-1";
  private String accessKey;
  private String secretKey;
  private boolean pathStyle = true;
  private String filesystemBasePath = System.getProperty("java.io.tmpdir") + "/platform-blobs";
  private int presignUrlTtlMinutes = 60;

  public enum Type {
    minio,
    s3,
    filesystem
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public boolean isPathStyle() {
    return pathStyle;
  }

  public void setPathStyle(boolean pathStyle) {
    this.pathStyle = pathStyle;
  }

  public String getFilesystemBasePath() {
    return filesystemBasePath;
  }

  public void setFilesystemBasePath(String filesystemBasePath) {
    this.filesystemBasePath = filesystemBasePath;
  }

  public int getPresignUrlTtlMinutes() {
    return presignUrlTtlMinutes;
  }

  public void setPresignUrlTtlMinutes(int presignUrlTtlMinutes) {
    this.presignUrlTtlMinutes = presignUrlTtlMinutes;
  }
}
