package com.platform.ingestion.management.tcm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.common.storage.BlobStoreBuckets;
import com.platform.core.domain.ExecutionAttachment;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.ExecutionAttachmentRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.TestRunRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evidence files attached to a test-case execution. Bytes go to the platform {@link BlobStore}
 * (content-addressed); a row in {@code execution_attachments} carries the metadata + serialized
 * {@link BlobRef}. Deleting an attachment removes only the row — never the shared blob.
 */
@Service
@Transactional
public class ExecutionAttachmentService {

  /** Per-file size cap (confirmed). No count or type limit. */
  static final long MAX_FILE_BYTES = 30L * 1024 * 1024;

  private final ExecutionAttachmentRepository attachmentRepo;
  private final TestCaseExecutionRepository execRepo;
  private final TestRunRepository runRepo;
  private final BlobStore blobStore;
  private final ObjectMapper om;

  public ExecutionAttachmentService(
      ExecutionAttachmentRepository attachmentRepo,
      TestCaseExecutionRepository execRepo,
      TestRunRepository runRepo,
      BlobStore blobStore,
      ObjectMapper om) {
    this.attachmentRepo = attachmentRepo;
    this.execRepo = execRepo;
    this.runRepo = runRepo;
    this.blobStore = blobStore;
    this.om = om;
  }

  /** Holder for a streamed download. */
  public record AttachmentBytes(String fileName, String contentType, byte[] bytes) {}

  @Transactional(readOnly = true)
  public List<ExecutionAttachmentDto> list(UUID projectId, UUID runId, UUID execId) {
    loadRun(projectId, runId);
    loadExec(runId, execId);
    return attachmentRepo.findByExecutionIdOrderByUploadedAtAsc(execId).stream()
        .map(ExecutionAttachmentDto::from)
        .toList();
  }

  public ExecutionAttachmentDto upload(
      UUID projectId, UUID runId, UUID execId, MultipartFile file, String uploadedBy) {
    requireEditable(loadRun(projectId, runId));
    loadExec(runId, execId);

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

    ExecutionAttachment row =
        new ExecutionAttachment(
            execId, runId, fileName, contentType, bytes.length, serialize(ref), uploadedBy);
    return ExecutionAttachmentDto.from(attachmentRepo.save(row));
  }

  @Transactional(readOnly = true)
  public AttachmentBytes download(UUID projectId, UUID runId, UUID attachmentId) {
    ExecutionAttachment a = loadAttachment(projectId, runId, attachmentId);
    BlobRef ref = deserialize(a.getBlobRef());
    byte[] bytes =
        blobStore
            .fetchBytes(ref)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Attachment bytes not found in storage"));
    return new AttachmentBytes(a.getFileName(), a.getContentType(), bytes);
  }

  /** Removes the attachment row only — the content-addressed blob is shared and left intact. */
  public void delete(UUID projectId, UUID runId, UUID attachmentId) {
    ExecutionAttachment a = loadAttachment(projectId, runId, attachmentId);
    requireEditable(loadRun(projectId, runId));
    attachmentRepo.delete(a);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private TestRun loadRun(UUID projectId, UUID runId) {
    TestRun run =
        runRepo
            .findById(runId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Test run not found: " + runId));
    if (!run.getProjectId().equals(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test run not found for project");
    }
    return run;
  }

  private TestCaseExecution loadExec(UUID runId, UUID execId) {
    TestCaseExecution exec =
        execRepo
            .findById(execId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Execution not found: " + execId));
    if (!exec.getTestRunId().equals(runId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found for run");
    }
    return exec;
  }

  private ExecutionAttachment loadAttachment(UUID projectId, UUID runId, UUID attachmentId) {
    loadRun(projectId, runId);
    ExecutionAttachment a =
        attachmentRepo
            .findById(attachmentId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Attachment not found: " + attachmentId));
    if (!a.getTestRunId().equals(runId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found for run");
    }
    return a;
  }

  private void requireEditable(TestRun run) {
    if (!run.isEditable()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run is " + run.getStatus() + "; reopen it before editing.");
    }
  }

  private String serialize(BlobRef ref) {
    try {
      return om.writeValueAsString(ref);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not record attachment");
    }
  }

  private BlobRef deserialize(String json) {
    try {
      return om.readValue(json, BlobRef.class);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Corrupt attachment reference");
    }
  }
}
