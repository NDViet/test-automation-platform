package com.platform.agent.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.GenerationClarification;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.GenerationClarificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GenerationStatusServiceTest {

  @Mock AgentWorkflowRepository workflowRepo;
  @Mock GenerationClarificationRepository clarificationRepo;

  GenerationStatusService service;
  private final UUID projectId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new GenerationStatusService(workflowRepo, clarificationRepo, new ObjectMapper());
  }

  private AgentWorkflow workflow(String status) {
    AgentWorkflow wf = new AgentWorkflow(projectId, "MANUAL", null, java.util.Map.of());
    if ("AWAITING_INPUT".equals(status)) wf.markAwaitingInput();
    else if ("COMPLETED".equals(status)) wf.markCompleted();
    when(workflowRepo.findById(workflowId)).thenReturn(Optional.of(wf));
    return wf;
  }

  @Test
  void parkedRunExposesPendingQuestions() {
    workflow("AWAITING_INPUT");
    GenerationClarification clar =
        new GenerationClarification(
            workflowId, 1, "[{\"id\":\"q1\",\"question\":\"Which browsers?\"}]", "chk-1");
    when(clarificationRepo.findByWorkflowIdOrderByRoundAsc(workflowId)).thenReturn(List.of(clar));

    GenerationStatusService.GenerationStatusDto dto = service.getStatus(projectId, workflowId);

    assertThat(dto.status()).isEqualTo("AWAITING_INPUT");
    assertThat(dto.rounds()).hasSize(1);
    assertThat(dto.pending()).isNotNull();
    assertThat(dto.pending().round()).isEqualTo(1);
    assertThat(dto.pending().questions().toString()).contains("Which browsers?");
  }

  @Test
  void completedRunHasNoPending() {
    workflow("COMPLETED");
    GenerationClarification clar = new GenerationClarification(workflowId, 1, "[]", "chk-1");
    clar.markAnswered("[{\"id\":\"q1\",\"answer\":\"chrome\"}]");
    when(clarificationRepo.findByWorkflowIdOrderByRoundAsc(workflowId)).thenReturn(List.of(clar));

    GenerationStatusService.GenerationStatusDto dto = service.getStatus(projectId, workflowId);

    assertThat(dto.status()).isEqualTo("COMPLETED");
    assertThat(dto.pending()).isNull();
    assertThat(dto.rounds()).hasSize(1);
    assertThat(dto.rounds().get(0).answers().toString()).contains("chrome");
  }

  @Test
  void rejectsCrossProjectAccess() {
    AgentWorkflow wf = new AgentWorkflow(UUID.randomUUID(), "MANUAL", null, java.util.Map.of());
    when(workflowRepo.findById(workflowId)).thenReturn(Optional.of(wf));

    assertThatThrownBy(() -> service.getStatus(projectId, workflowId))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
  }
}
