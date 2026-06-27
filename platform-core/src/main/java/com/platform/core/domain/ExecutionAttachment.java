package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evidence file attached to a single {@link TestCaseExecution}. The bytes live in the platform
 * BlobStore (content-addressed); this row carries the metadata plus the serialized {@code BlobRef}
 * needed to fetch them. Deleting a row never removes the shared blob.
 */
@Entity
@Table(name = "execution_attachments")
public class ExecutionAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "execution_id", nullable = false)
  private UUID executionId;

  @Column(name = "test_run_id", nullable = false)
  private UUID testRunId;

  @Column(name = "file_name", nullable = false, length = 300)
  private String fileName;

  @Column(name = "content_type", length = 150)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  /** Serialized {@code BlobRef} (JSON) locating the bytes in the object store. */
  @Column(name = "blob_ref", nullable = false, length = 500)
  private String blobRef;

  @Column(name = "uploaded_by", length = 200)
  private String uploadedBy;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt = Instant.now();

  protected ExecutionAttachment() {}

  public ExecutionAttachment(
      UUID executionId,
      UUID testRunId,
      String fileName,
      String contentType,
      long sizeBytes,
      String blobRef,
      String uploadedBy) {
    this.executionId = executionId;
    this.testRunId = testRunId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.blobRef = blobRef;
    this.uploadedBy = uploadedBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public UUID getTestRunId() {
    return testRunId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getBlobRef() {
    return blobRef;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExecutionAttachment a)) return false;
    return Objects.equals(id, a.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
