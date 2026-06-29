package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.GenerateTestCasesRequest;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.AiGenerationRun;
import com.platform.core.repository.AiGenerationRunRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseGenerationControllerTest {

  @Mock AgentWorkflowService workflowService;
  @Mock ContextAssembler contextAssembler;
  @Mock GenerationInputService inputService;
  @Mock AiGenerationRunRepository runRepo;
  @Mock com.platform.agent.workflow.GenerationResumeService resumeService;
  @Mock com.platform.agent.workflow.GenerationStatusService statusService;

  TestCaseGenerationController controller;
  private final UUID projectId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    controller =
        new TestCaseGenerationController(
            workflowService,
            contextAssembler,
            new ObjectMapper(),
            inputService,
            runRepo,
            resumeService,
            statusService);
    AgentWorkflow wf = mock(AgentWorkflow.class);
    when(wf.getId()).thenReturn(workflowId);
    when(workflowService.createWorkflow(eq(projectId), any())).thenReturn(wf);
    when(contextAssembler.assemble(any(), any(), any())).thenReturn(mock(ContextBundle.class));
  }

  @Test
  void emptyBodyGeneratesAllWithoutPersistingRunOrValidating() {
    var resp = controller.generateTestCases(projectId, null);

    assertThat(resp.getStatusCode().value()).isEqualTo(202);
    verify(workflowService).executeWorkflow(eq(workflowId), any());
    verify(runRepo, never()).save(any());
    verify(inputService, never()).validate(any(), any());
  }

  @Test
  void richBodyValidatesAndPersistsRun() {
    String body =
        "{\"freeText\":\"test the login flow\",\"skillIds\":[],\"maxRounds\":3}";

    var resp = controller.generateTestCases(projectId, body);

    assertThat(resp.getStatusCode().value()).isEqualTo(202);
    verify(inputService).validate(eq(projectId), any(GenerateTestCasesRequest.class));
    verify(runRepo).save(any(AiGenerationRun.class));
    verify(workflowService).executeWorkflow(eq(workflowId), any());
  }
}
