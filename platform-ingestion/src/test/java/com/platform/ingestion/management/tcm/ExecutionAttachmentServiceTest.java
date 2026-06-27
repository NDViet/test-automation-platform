package com.platform.ingestion.management.tcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.core.domain.ExecutionAttachment;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.ExecutionAttachmentRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.TestRunRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ExecutionAttachmentServiceTest {

  private ExecutionAttachmentRepository attachmentRepo;
  private TestCaseExecutionRepository execRepo;
  private TestRunRepository runRepo;
  private BlobStore blobStore;
  private ExecutionAttachmentService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID runId = UUID.randomUUID();
  private final UUID execId = UUID.randomUUID();
  private final UUID attId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    attachmentRepo = mock(ExecutionAttachmentRepository.class);
    execRepo = mock(TestCaseExecutionRepository.class);
    runRepo = mock(TestRunRepository.class);
    blobStore = mock(BlobStore.class);
    service =
        new ExecutionAttachmentService(
            attachmentRepo, execRepo, runRepo, blobStore, new ObjectMapper());

    TestRun run = new TestRun(projectId, "Run", "1.0", "STAGING", "alice"); // IN_PROGRESS
    lenient().when(runRepo.findById(runId)).thenReturn(Optional.of(run));
    lenient()
        .when(execRepo.findById(execId))
        .thenReturn(Optional.of(new TestCaseExecution(runId, UUID.randomUUID())));
    lenient().when(attachmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private MultipartFile file(long size, byte[] bytes) throws Exception {
    MultipartFile f = mock(MultipartFile.class);
    when(f.isEmpty()).thenReturn(false);
    when(f.getSize()).thenReturn(size);
    lenient().when(f.getBytes()).thenReturn(bytes);
    lenient().when(f.getOriginalFilename()).thenReturn("screenshot.png");
    lenient().when(f.getContentType()).thenReturn("image/png");
    return f;
  }

  @Test
  void uploadStoresBlobAndPersistsMetadata() throws Exception {
    byte[] bytes = "hello".getBytes();
    when(blobStore.storeBytes(any(), any(), eq("image/png")))
        .thenReturn(
            new BlobRef("platform-artifacts", "ab/abcd", "abcd", "image/png", bytes.length));

    ExecutionAttachmentDto dto =
        service.upload(projectId, runId, execId, file(bytes.length, bytes), "bob");

    assertThat(dto.fileName()).isEqualTo("screenshot.png");
    assertThat(dto.contentType()).isEqualTo("image/png");
    assertThat(dto.sizeBytes()).isEqualTo(bytes.length);
    assertThat(dto.uploadedBy()).isEqualTo("bob");
    verify(blobStore).storeBytes(any(), any(), eq("image/png"));
    verify(attachmentRepo).save(any(ExecutionAttachment.class));
  }

  @Test
  void uploadRejectsFilesOver30Mb() throws Exception {
    MultipartFile big = file(30L * 1024 * 1024 + 1, new byte[0]);

    assertThatThrownBy(() -> service.upload(projectId, runId, execId, big, "bob"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

    verify(blobStore, never()).storeBytes(any(), any(), any());
    verify(attachmentRepo, never()).save(any());
  }

  @Test
  void uploadRejectedWhenRunCompleted() throws Exception {
    TestRun completed = new TestRun(projectId, "Run", "1.0", "STAGING", "alice");
    completed.complete();
    when(runRepo.findById(runId)).thenReturn(Optional.of(completed));

    assertThatThrownBy(() -> service.upload(projectId, runId, execId, file(5, new byte[5]), "bob"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    verify(blobStore, never()).storeBytes(any(), any(), any());
  }

  @Test
  void deleteRemovesRowButNotBlob() {
    ExecutionAttachment a =
        new ExecutionAttachment(
            execId, runId, "f.png", "image/png", 10, "{\"key\":\"ab/abcd\"}", "bob");
    when(attachmentRepo.findById(attId)).thenReturn(Optional.of(a));

    service.delete(projectId, runId, attId);

    verify(attachmentRepo).delete(a);
    verify(blobStore, never())
        .delete(any()); // content-addressed blob is shared — never hard-deleted
  }
}
