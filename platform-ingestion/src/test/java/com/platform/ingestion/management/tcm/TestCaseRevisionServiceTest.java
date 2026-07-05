package com.platform.ingestion.management.tcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseRevision;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseRevisionRepository;
import com.platform.core.repository.TestCaseStepRepository;
import com.platform.core.repository.TestCaseTagRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseRevisionServiceTest {

  @Mock PlatformTestCaseRepository testCaseRepo;
  @Mock TestCaseStepRepository stepRepo;
  @Mock TestCaseTagRepository tagRepo;
  @Mock TestCaseRevisionRepository revisionRepo;

  TestCaseRevisionService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID tcId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new TestCaseRevisionService(
            testCaseRepo, stepRepo, tagRepo, revisionRepo, new ObjectMapper());
  }

  @Test
  void snapshotWritesNextVersionWithJson() {
    when(testCaseRepo.findById(tcId))
        .thenReturn(Optional.of(new PlatformTestCase(pid, "Login flow", List.of(), "HUMAN", null)));
    when(stepRepo.findByTestCaseIdOrderByStepNumberAsc(tcId)).thenReturn(List.of());
    when(tagRepo.findByTestCaseIdOrderByNameAsc(tcId)).thenReturn(List.of());
    when(revisionRepo.countByTestCaseId(tcId)).thenReturn(2);

    service.snapshot(pid, tcId, "UPDATE");

    ArgumentCaptor<TestCaseRevision> cap = ArgumentCaptor.forClass(TestCaseRevision.class);
    verify(revisionRepo).save(cap.capture());
    assertThat(cap.getValue().getVersion()).isEqualTo(3);
    assertThat(cap.getValue().getChangeKind()).isEqualTo("UPDATE");
    assertThat(cap.getValue().getSnapshot()).contains("Login flow");
  }

  @Test
  void snapshotOfMissingCaseIsNoop() {
    when(testCaseRepo.findById(tcId)).thenReturn(Optional.empty());

    service.snapshot(pid, tcId, "UPDATE");

    verify(revisionRepo, never()).save(org.mockito.ArgumentMatchers.any());
  }
}
