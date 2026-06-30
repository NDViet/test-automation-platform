package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.agents.AgentResolutionService;
import com.platform.agent.agents.EffectiveAgentConfig;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.common.agent.AgentTaskType;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.GenerateTestCasesRequest;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.AiGenerationRun;
import com.platform.core.repository.AiGenerationRunRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @Mock AgentResolutionService agentResolutionService;
  @Mock com.platform.agent.proposals.ProposalService proposalService;

  TestCaseGenerationController controller;
  private final UUID projectId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();
  private final UUID testCaseId = UUID.randomUUID();

  private static EffectiveAgentConfig seedCfg() {
    return new EffectiveAgentConfig(
        EffectiveAgentConfig.Source.SEED,
        null,
        "SYS",
        "USR",
        null,
        "COMPLEX",
        List.of(),
        Map.of(),
        0);
  }

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
            statusService,
            agentResolutionService,
            proposalService);
    AgentWorkflow wf = mock(AgentWorkflow.class);
    when(wf.getId()).thenReturn(workflowId);
    when(workflowService.createWorkflow(eq(projectId), any())).thenReturn(wf);
    when(contextAssembler.assemble(any(), any(), any())).thenReturn(mock(ContextBundle.class));
    lenient()
        .when(agentResolutionService.resolve(any(), any(), any(), any()))
        .thenReturn(seedCfg());
    lenient().when(agentResolutionService.effectiveSubType(any(), any())).thenReturn("FUNCTIONAL");
  }

  @Test
  void emptyBodyResolvesSeedAndPersistsRunWithoutValidating() {
    var resp = controller.generateTestCases(projectId, null);

    assertThat(resp.getStatusCode().value()).isEqualTo(202);
    verify(workflowService).executeWorkflow(eq(workflowId), any());
    verify(runRepo)
        .save(any(AiGenerationRun.class)); // always records the resolved (seed) agent now
    verify(inputService, never()).validate(any(), any());
  }

  @Test
  void richBodyValidatesAndPersistsRun() {
    String body = "{\"freeText\":\"test the login flow\",\"skillIds\":[],\"maxRounds\":3}";

    var resp = controller.generateTestCases(projectId, body);

    assertThat(resp.getStatusCode().value()).isEqualTo(202);
    verify(inputService).validate(eq(projectId), any(GenerateTestCasesRequest.class));
    verify(runRepo).save(any(AiGenerationRun.class));
    verify(workflowService).executeWorkflow(eq(workflowId), any());
  }

  @Test
  void recordsResolvedAgentAndSubTypeOnRun() {
    UUID agentId = UUID.randomUUID();
    when(agentResolutionService.resolve(
            eq(projectId),
            eq(AgentTaskType.GENERATE_TEST_CASES),
            eq("NON_FUNCTIONAL"),
            eq(agentId)))
        .thenReturn(
            new EffectiveAgentConfig(
                EffectiveAgentConfig.Source.PROJECT,
                agentId,
                "SYS",
                "USR",
                "gpt-4o",
                "COMPLEX",
                List.of(),
                Map.of(),
                2));
    when(agentResolutionService.effectiveSubType(any(), eq("NON_FUNCTIONAL")))
        .thenReturn("NON_FUNCTIONAL");
    String body = "{\"agentId\":\"" + agentId + "\",\"subType\":\"NON_FUNCTIONAL\"}";

    ArgumentCaptor<AiGenerationRun> cap = ArgumentCaptor.forClass(AiGenerationRun.class);
    controller.generateTestCases(projectId, body);

    verify(runRepo).save(cap.capture());
    assertThat(cap.getValue().getAgentId()).isEqualTo(agentId);
    assertThat(cap.getValue().getTaskSubType()).isEqualTo("NON_FUNCTIONAL");
    assertThat(cap.getValue().getResolvedModelId()).isEqualTo("gpt-4o");
  }

  @Test
  void automationResolvesAgentAndRecordsRun() {
    ArgumentCaptor<AiGenerationRun> cap = ArgumentCaptor.forClass(AiGenerationRun.class);

    var resp = controller.generateAutomation(projectId, testCaseId, "{}");

    assertThat(resp.getStatusCode().value()).isEqualTo(202);
    verify(agentResolutionService)
        .resolve(eq(projectId), eq(AgentTaskType.GENERATE_AUTOMATION_CODE), any(), any());
    verify(runRepo).save(cap.capture());
    assertThat(cap.getValue().getWorkflowId()).isEqualTo(workflowId);
    verify(workflowService).executeWorkflow(eq(workflowId), any());
  }
}
