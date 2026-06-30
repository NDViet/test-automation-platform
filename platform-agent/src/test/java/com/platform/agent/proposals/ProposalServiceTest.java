package com.platform.agent.proposals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.proposals.ProposalDtos.ProposalDto;
import com.platform.core.domain.GeneratedTestCaseProposal;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseStep;
import com.platform.core.repository.GeneratedTestCaseProposalRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseStepRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

  @Mock GeneratedTestCaseProposalRepository proposalRepo;
  @Mock PlatformTestCaseRepository testCaseRepo;
  @Mock TestCaseStepRepository stepRepo;
  ProposalService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ProposalService(proposalRepo, testCaseRepo, stepRepo, new ObjectMapper());
  }

  private GeneratedTestCaseProposal proposal(UUID proj, int ordinal, String title, String steps) {
    return new GeneratedTestCaseProposal(
        workflowId, proj, ordinal, title, "d", "p", "e", "HIGH", "req-1", steps);
  }

  @Test
  void listMapsProposalsAndParsesSteps() {
    String steps = "[{\"action\":\"open\",\"expectedResult\":\"shown\",\"notes\":null}]";
    when(proposalRepo.findByWorkflowIdOrderByOrdinalAsc(workflowId))
        .thenReturn(List.of(proposal(projectId, 0, "TC A", steps)));

    List<ProposalDto> dtos = service.list(projectId, workflowId);

    assertThat(dtos).hasSize(1);
    ProposalDto dto = dtos.get(0);
    assertThat(dto.title()).isEqualTo("TC A");
    assertThat(dto.status()).isEqualTo("PROPOSED");
    assertThat(dto.steps()).hasSize(1);
    assertThat(dto.steps().get(0).action()).isEqualTo("open");
  }

  @Test
  void listExcludesProposalsFromOtherProjects() {
    when(proposalRepo.findByWorkflowIdOrderByOrdinalAsc(workflowId))
        .thenReturn(
            List.of(
                proposal(projectId, 0, "mine", "[]"),
                proposal(UUID.randomUUID(), 1, "someone else's", "[]")));

    List<ProposalDto> dtos = service.list(projectId, workflowId);

    assertThat(dtos).extracting(ProposalDto::title).containsExactly("mine");
  }

  @Test
  void acceptCreatesDraftWithStepsAndMarksAccepted() {
    UUID pid = UUID.randomUUID();
    UUID tcId = UUID.randomUUID();
    String steps = "[{\"action\":\"a1\",\"expectedResult\":\"e1\",\"notes\":null}]";
    GeneratedTestCaseProposal p = proposal(projectId, 0, "Login", steps);
    when(proposalRepo.findByIdAndProjectId(pid, projectId)).thenReturn(Optional.of(p));
    PlatformTestCase savedTc = mock(PlatformTestCase.class);
    when(savedTc.getId()).thenReturn(tcId);
    when(testCaseRepo.save(any(PlatformTestCase.class))).thenReturn(savedTc);
    when(proposalRepo.save(any())).thenAnswer(i -> i.getArgument(0));

    ProposalDto dto = service.accept(projectId, pid, "alice");

    ArgumentCaptor<PlatformTestCase> tc = ArgumentCaptor.forClass(PlatformTestCase.class);
    verify(testCaseRepo).save(tc.capture());
    assertThat(tc.getValue().getStatus()).isEqualTo("DRAFT");
    assertThat(tc.getValue().getTitle()).isEqualTo("Login");
    verify(stepRepo, times(1)).save(any(TestCaseStep.class));
    assertThat(p.getStatus()).isEqualTo("ACCEPTED");
    assertThat(dto.status()).isEqualTo("ACCEPTED");
    assertThat(dto.acceptedTestCaseId()).isEqualTo(tcId);
  }

  @Test
  void acceptIsIdempotentForAlreadyAccepted() {
    UUID pid = UUID.randomUUID();
    GeneratedTestCaseProposal p = proposal(projectId, 0, "Login", "[]");
    p.markAccepted(UUID.randomUUID());
    when(proposalRepo.findByIdAndProjectId(pid, projectId)).thenReturn(Optional.of(p));

    ProposalDto dto = service.accept(projectId, pid, "alice");

    assertThat(dto.status()).isEqualTo("ACCEPTED");
    verify(testCaseRepo, never()).save(any());
  }

  @Test
  void acceptRejectedProposalConflicts() {
    UUID pid = UUID.randomUUID();
    GeneratedTestCaseProposal p = proposal(projectId, 0, "Login", "[]");
    p.markRejected();
    when(proposalRepo.findByIdAndProjectId(pid, projectId)).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> service.accept(projectId, pid, "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Cannot accept");
    verify(testCaseRepo, never()).save(any());
  }

  @Test
  void rejectMarksRejectedAndNeverPersistsCatalog() {
    UUID pid = UUID.randomUUID();
    GeneratedTestCaseProposal p = proposal(projectId, 0, "Login", "[]");
    when(proposalRepo.findByIdAndProjectId(pid, projectId)).thenReturn(Optional.of(p));
    when(proposalRepo.save(any())).thenAnswer(i -> i.getArgument(0));

    ProposalDto dto = service.reject(projectId, pid);

    assertThat(dto.status()).isEqualTo("REJECTED");
    assertThat(p.getStatus()).isEqualTo("REJECTED");
    verify(testCaseRepo, never()).save(any());
  }

  @Test
  void rejectAcceptedProposalConflicts() {
    UUID pid = UUID.randomUUID();
    GeneratedTestCaseProposal p = proposal(projectId, 0, "Login", "[]");
    p.markAccepted(UUID.randomUUID());
    when(proposalRepo.findByIdAndProjectId(pid, projectId)).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> service.reject(projectId, pid))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Cannot reject");
  }

  @Test
  void acceptAllAcceptsEveryProposedCase() {
    GeneratedTestCaseProposal p1 = proposal(projectId, 0, "A", "[]");
    GeneratedTestCaseProposal p2 = proposal(projectId, 1, "B", "[]");
    when(proposalRepo.findByWorkflowIdAndStatusOrderByOrdinalAsc(workflowId, "PROPOSED"))
        .thenReturn(List.of(p1, p2));
    when(testCaseRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(proposalRepo.save(any())).thenAnswer(i -> i.getArgument(0));

    List<ProposalDto> dtos = service.acceptAll(projectId, workflowId, "alice");

    assertThat(dtos).hasSize(2).allMatch(d -> "ACCEPTED".equals(d.status()));
    verify(testCaseRepo, times(2)).save(any());
    assertThat(p1.getStatus()).isEqualTo("ACCEPTED");
    assertThat(p2.getStatus()).isEqualTo("ACCEPTED");
  }
}
