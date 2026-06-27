package com.platform.ingestion.management.tcm;

import com.platform.core.domain.ExecutionAttachment;

public record ExecutionAttachmentDto(
    String id,
    String executionId,
    String fileName,
    String contentType,
    long sizeBytes,
    String uploadedBy,
    String uploadedAt) {
  public static ExecutionAttachmentDto from(ExecutionAttachment a) {
    return new ExecutionAttachmentDto(
        a.getId() != null ? a.getId().toString() : null,
        a.getExecutionId() != null ? a.getExecutionId().toString() : null,
        a.getFileName(),
        a.getContentType(),
        a.getSizeBytes(),
        a.getUploadedBy(),
        a.getUploadedAt() != null ? a.getUploadedAt().toString() : null);
  }
}
