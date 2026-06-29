package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.agent.GenerateTestCasesRequest;
import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.core.domain.AiGenerationFile;
import com.platform.core.domain.AiSkill;
import com.platform.core.repository.AiGenerationFileRepository;
import com.platform.core.repository.AiSkillRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GenerationInputServiceTest {

  @Mock AiGenerationFileRepository fileRepo;
  @Mock AiSkillRepository skillRepo;
  @Mock BlobStore blobStore;

  GenerationInputService service;
  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new GenerationInputService(fileRepo, skillRepo, blobStore, new ObjectMapper());
  }

  private GenerateTestCasesRequest req(
      List<String> reqIds, String freeText, List<String> fileIds, List<String> skillIds) {
    return new GenerateTestCasesRequest(
        reqIds, freeText, fileIds, skillIds, null, null, null, null, null);
  }

  @Test
  void validateRejectsWhenNoInputSource() {
    assertThatThrownBy(
            () -> service.validate(projectId, req(List.of(), "  ", List.of(), List.of())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
  }

  @Test
  void validatePassesWithFreeTextOnly() {
    service.validate(projectId, req(List.of(), "test the login flow", List.of(), List.of()));
    // no throw
  }

  @Test
  void validateRejectsUnknownSkillWith404() {
    UUID skillId = UUID.randomUUID();
    when(skillRepo.findById(skillId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.validate(
                    projectId, req(List.of(), "x", List.of(), List.of(skillId.toString()))))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
  }

  @Test
  void validateRejectsCrossProjectSkillWith404() {
    UUID skillId = UUID.randomUUID();
    AiSkill foreign = new AiSkill(UUID.randomUUID(), "x", null, "i", true, "bob");
    when(skillRepo.findById(skillId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () ->
                service.validate(
                    projectId, req(List.of(), "x", List.of(), List.of(skillId.toString()))))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
  }

  @Test
  void validateRejectsUnknownFileWith400() {
    UUID fileId = UUID.randomUUID();
    when(fileRepo.findById(fileId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.validate(
                    projectId, req(List.of(), null, List.of(fileId.toString()), List.of())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
  }

  @Test
  void uploadStoresBytesInArtifactsAndPersistsRow() {
    MockMultipartFile file =
        new MockMultipartFile("file", "spec.txt", "text/plain", "hello".getBytes());
    BlobRef ref = new BlobRef("platform-artifacts", "ab/abc", "abc", "text/plain", 5);
    when(blobStore.storeBytes(eq("platform-artifacts"), any(), eq("text/plain"))).thenReturn(ref);
    when(fileRepo.save(any(AiGenerationFile.class))).thenAnswer(inv -> inv.getArgument(0));

    AiGenerationFileDto dto = service.upload(projectId, file, "alice");

    assertThat(dto.fileName()).isEqualTo("spec.txt");
    assertThat(dto.sizeBytes()).isEqualTo(5);
    verify(blobStore).storeBytes(eq("platform-artifacts"), any(), eq("text/plain"));
    verify(fileRepo).save(any(AiGenerationFile.class));
  }

  @Test
  void uploadRejectsEmptyFile() {
    MockMultipartFile empty = new MockMultipartFile("file", "e.txt", "text/plain", new byte[0]);
    assertThatThrownBy(() -> service.upload(projectId, empty, "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    verify(fileRepo, never()).save(any());
  }

  private static int status(Throwable e) {
    return ((ResponseStatusException) e).getStatusCode().value();
  }
}
