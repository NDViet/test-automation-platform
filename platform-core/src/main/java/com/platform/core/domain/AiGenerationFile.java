package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An input file uploaded for an AI test-case generation run (spec, mockup, CSV, …). The bytes live
 * in the platform BlobStore (ARTIFACTS, content-addressed); this row carries the metadata plus the
 * serialized {@code BlobRef}. Project-scoped so files can only be referenced by their owning
 * project.
 */
@Entity
@Table(name = "ai_generation_files")
public class AiGenerationFile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

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

  protected AiGenerationFile() {}

  public AiGenerationFile(
      UUID projectId,
      String fileName,
      String contentType,
      long sizeBytes,
      String blobRef,
      String uploadedBy) {
    this.projectId = projectId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.blobRef = blobRef;
    this.uploadedBy = uploadedBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
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
    if (!(o instanceof AiGenerationFile f)) return false;
    return Objects.equals(id, f.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
