package com.platform.ingestion.management.tcm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseStepRepository;
import com.platform.core.repository.TestSuiteRepository;
import com.platform.ingestion.audit.AuditWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TestCaseManagementAuditTest {

  @Mock PlatformTestCaseRepository testCaseRepo;
  @Mock TestCaseStepRepository stepRepo;
  @Mock NamedParameterJdbcTemplate njdbc;
  @Mock SuiteResolverService suiteResolver;
  @Mock TestSuiteRepository suiteRepo;
  @Mock AuditWriter audit;
  @Mock TestCaseRevisionService revisions;
  @Mock com.platform.core.repository.TestCaseTagRepository tagRepo;
  @Mock com.platform.core.repository.SharedStepRepository sharedStepRepo;

  TestCaseManagementService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID tcId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new TestCaseManagementService(
            testCaseRepo,
            stepRepo,
            njdbc,
            suiteResolver,
            suiteRepo,
            audit,
            revisions,
            tagRepo,
            sharedStepRepo);
    PlatformTestCase tc = new PlatformTestCase(pid, "Login", List.of(), "HUMAN", null);
    when(testCaseRepo.findById(tcId)).thenReturn(Optional.of(tc));
    lenient().when(testCaseRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    lenient().when(stepRepo.findByTestCaseIdOrderByStepNumberAsc(tcId)).thenReturn(List.of());
  }

  @Test
  void approveWritesAuditEvent() {
    service.approve(pid, tcId);
    verify(audit)
        .record(eq("TEST_CASE_APPROVED"), eq(pid), eq("TEST_CASE"), eq(tcId.toString()), any());
  }

  @Test
  void rejectWritesAuditEvent() {
    service.reject(pid, tcId);
    verify(audit)
        .record(eq("TEST_CASE_REJECTED"), eq(pid), eq("TEST_CASE"), eq(tcId.toString()), any());
  }

  @Test
  void deleteWritesAuditEvent() {
    service.delete(pid, tcId);
    verify(audit)
        .record(eq("TEST_CASE_DELETED"), eq(pid), eq("TEST_CASE"), eq(tcId.toString()), any());
  }

  @Test
  void stepReferencingSharedStepResolvesToSharedContent() {
    UUID ssId = UUID.randomUUID();
    com.platform.core.domain.TestCaseStep step =
        new com.platform.core.domain.TestCaseStep(tcId, 1, "orig", "origExp", null);
    step.setSharedStepId(ssId);
    when(stepRepo.findByTestCaseIdOrderByStepNumberAsc(tcId)).thenReturn(List.of(step));
    when(sharedStepRepo.findById(ssId))
        .thenReturn(
            Optional.of(
                new com.platform.core.domain.SharedStep(pid, "Shared login", "logged in", null)));

    var dto = service.get(pid, tcId);

    org.assertj.core.api.Assertions.assertThat(dto.steps()).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(dto.steps().get(0).action())
        .isEqualTo("Shared login");
    org.assertj.core.api.Assertions.assertThat(dto.steps().get(0).sharedStepId())
        .isEqualTo(ssId.toString());
  }

  @Test
  void bulkDeleteRemovesCaseStepsAndTags() {
    var result = service.bulk(pid, List.of(tcId), "DELETE", null);

    org.assertj.core.api.Assertions.assertThat(result.processed()).isEqualTo(1);
    verify(stepRepo).deleteAllByTestCaseId(tcId);
    verify(tagRepo).deleteAllByTestCaseId(tcId);
    verify(testCaseRepo).delete(org.mockito.ArgumentMatchers.any());
    verify(audit)
        .record(eq("TEST_CASE_DELETED"), eq(pid), eq("TEST_CASE"), eq(tcId.toString()), any());
  }

  @Test
  void bulkApproveProcessesEachValidIdAndSkipsUnknown() {
    UUID other = UUID.randomUUID();
    when(testCaseRepo.findById(other)).thenReturn(Optional.empty());

    var result = service.bulk(pid, List.of(tcId, other), "APPROVE", null);

    org.assertj.core.api.Assertions.assertThat(result.processed()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(result.skipped()).containsExactly(other.toString());
    verify(audit)
        .record(eq("TEST_CASE_APPROVED"), eq(pid), eq("TEST_CASE"), eq(tcId.toString()), any());
  }
}
