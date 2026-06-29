package com.platform.agent.api;

import com.platform.core.domain.AiGenerationFile;
import java.time.Instant;
import java.util.UUID;

/** API view of an uploaded generation input file. */
public record AiGenerationFileDto(
    UUID id,
    UUID projectId,
    String fileName,
    String contentType,
    long sizeBytes,
    String uploadedBy,
    Instant uploadedAt) {

  public static AiGenerationFileDto from(AiGenerationFile f) {
    return new AiGenerationFileDto(
        f.getId(),
        f.getProjectId(),
        f.getFileName(),
        f.getContentType(),
        f.getSizeBytes(),
        f.getUploadedBy(),
        f.getUploadedAt());
  }
}
