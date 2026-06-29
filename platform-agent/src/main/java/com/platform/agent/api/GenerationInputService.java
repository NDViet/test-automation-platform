package com.platform.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.agent.GenerateTestCasesRequest;
import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.common.storage.BlobStoreBuckets;
import com.platform.core.domain.AiGenerationFile;
import com.platform.core.repository.AiGenerationFileRepository;
import com.platform.core.repository.AiSkillRepository;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles generation input: uploading reference files into the BlobStore and validating a {@link
 * GenerateTestCasesRequest} against the project's skills and files. Validation guarantees at least
 * one input source and that every referenced skill/file belongs to the project.
 */
@Service
public class GenerationInputService {

  private static final long MAX_FILE_BYTES = 30L * 1024 * 1024; // 30 MB

  private final AiGenerationFileRepository fileRepo;
  private final AiSkillRepository skillRepo;
  private final BlobStore blobStore;
  private final ObjectMapper mapper;

  public GenerationInputService(
      AiGenerationFileRepository fileRepo,
      AiSkillRepository skillRepo,
      BlobStore blobStore,
      ObjectMapper mapper) {
    this.fileRepo = fileRepo;
    this.skillRepo = skillRepo;
    this.blobStore = blobStore;
    this.mapper = mapper;
  }

  @Transactional
  public AiGenerationFileDto upload(UUID projectId, MultipartFile file, String uploadedBy) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "File exceeds the 30 MB limit (" + file.getSize() + " bytes)");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Could not read upload: " + e.getMessage());
    }
    String contentType =
        file.getContentType() != null ? file.getContentType() : "application/octet-stream";
    BlobRef ref = blobStore.storeBytes(BlobStoreBuckets.ARTIFACTS, bytes, contentType);
    String fileName = file.getOriginalFilename();
    if (fileName == null || fileName.isBlank()) fileName = "attachment";
    AiGenerationFile row =
        new AiGenerationFile(
            projectId, fileName, contentType, bytes.length, serialize(ref), uploadedBy);
    return AiGenerationFileDto.from(fileRepo.save(row));
  }

  /** Validate request input sources, skill ownership, and file ownership. Throws on any problem. */
  @Transactional(readOnly = true)
  public void validate(UUID projectId, GenerateTestCasesRequest req) {
    if (!req.hasAnyInput()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "At least one input is required: requirements, free text, or a file");
    }
    for (String skillId : req.skillIdsOrEmpty()) {
      var skill = skillRepo.findById(parse(skillId, HttpStatus.NOT_FOUND, "skill"));
      if (skill.isEmpty() || !skill.get().getProjectId().equals(projectId)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown skill: " + skillId);
      }
    }
    for (String fileId : req.fileIdsOrEmpty()) {
      var f = fileRepo.findById(parse(fileId, HttpStatus.BAD_REQUEST, "file"));
      if (f.isEmpty() || !f.get().getProjectId().equals(projectId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown file: " + fileId);
      }
    }
  }

  private static UUID parse(String id, HttpStatus onError, String what) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(onError, "Invalid " + what + " id: " + id);
    }
  }

  private String serialize(BlobRef ref) {
    try {
      return mapper.writeValueAsString(ref);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not record uploaded file");
    }
  }
}
