package com.platform.agent.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.contract.AgentGridFixtures;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.node.impl.TestCaseGenerationNode;
import com.platform.common.agent.AgentTaskType;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.NodeResult;
import com.platform.common.agent.NodeType;
import com.platform.common.agent.TokenUsage;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.GenerationClarification;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.AiGenerationRunRepository;
import com.platform.core.repository.GenerationClarificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GenerationResumeServiceTest {

  @Mock AgentWorkflowRepository workflowRepo;
  @Mock GenerationClarificationRepository clarificationRepo;
  @Mock AiGenerationRunRepository runRepo;
  @Mock ContextAssembler contextAssembler;
  @Mock TestCaseGenerationNode generationNode;
  @Mock KafkaTemplate<String, String> kafka;

  GenerationResumeService service;
  private final UUID projectId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new GenerationResumeService(
            workflowRepo,
            clarificationRepo,
            runRepo,
            contextAssembler,
            generationNode,
            kafka,
            new ObjectMapper());
  }

  private AgentWorkflow workflow(String status) {
    AgentWorkflow wf = new AgentWorkflow(projectId, "MANUAL", null, java.util.Map.of());
    if ("AWAITING_INPUT".equals(status)) wf.markAwaitingInput();
    else if ("COMPLETED".equals(status)) wf.markCompleted();
    when(workflowRepo.findById(workflowId)).thenReturn(Optional.of(wf));
    return wf;
  }

  private List<GenerationResumeService.Answer> answers() {
    return List.of(new GenerationResumeService.Answer("q1", "chrome, firefox"));
  }

  @Test
  void markAnsweredRejectsWhenNotAwaitingInput() {
    workflow("COMPLETED");
    assertThatThrownBy(() -> service.markAnswered(projectId, workflowId, answers()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
  }

  @Test
  void markAnsweredRecordsAnswerFlipsRunningAndReturnsPlan() {
    AgentWorkflow wf = workflow("AWAITING_INPUT");
    GenerationClarification clar =
        new GenerationClarification(workflowId, 1, "[]", "chk-1");
    when(clarificationRepo.findFirstByWorkflowIdAndStatusOrderByRoundDesc(workflowId, "PENDING"))
        .thenReturn(Optional.of(clar));
    when(runRepo.findByWorkflowId(workflowId)).thenReturn(Optional.empty());
    when(clarificationRepo.countByWorkflowId(workflowId)).thenReturn(1L);

    GenerationResumeService.ResumePlan plan = service.markAnswered(projectId, workflowId, answers());

    assertThat(clar.getStatus()).isEqualTo("ANSWERED");
    assertThat(wf.getStatus()).isEqualTo("RUNNING");
    assertThat(plan.checkpointId()).isEqualTo("chk-1");
    assertThat(plan.answersText()).contains("chrome, firefox");
  }

  @Test
  void resumeAsyncCompletesWorkflowWhenModelFinishes() {
    AgentWorkflow wf = workflow("RUNNING");
    ContextBundle bundle = AgentGridFixtures.bundle();
    when(contextAssembler.resume(workflowId, "chk-1")).thenReturn(bundle);
    when(generationNode.resumeWithAnswers(eq(bundle), eq("chk-1"), any(), anyBoolean()))
        .thenReturn(
            NodeResult.completed(
                bundle.sessionId(),
                workflowId,
                NodeType.TEST_GENERATION,
                AgentTaskType.GENERATE_TEST_CASES,
                com.platform.common.agent.ArtifactManifest.empty(),
                "Generated 3 test cases.",
                TokenUsage.zero()));

    service.resumeAsync(
        workflowId, new GenerationResumeService.ResumePlan(projectId, "chk-1", "answers"), true);

    assertThat(wf.getStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void resumeAsyncReParksWorkflowWhenModelAsksAgain() {
    AgentWorkflow wf = workflow("RUNNING");
    ContextBundle bundle = AgentGridFixtures.bundle();
    when(contextAssembler.resume(workflowId, "chk-1")).thenReturn(bundle);
    when(clarificationRepo.countByWorkflowId(workflowId)).thenReturn(1L);
    when(generationNode.resumeWithAnswers(eq(bundle), eq("chk-1"), any(), anyBoolean()))
        .thenReturn(
            NodeResult.awaitingInput(
                bundle.sessionId(),
                workflowId,
                NodeType.TEST_GENERATION,
                AgentTaskType.GENERATE_TEST_CASES,
                "[{\"id\":\"q2\",\"question\":\"What data?\"}]",
                "chk-2",
                TokenUsage.zero()));

    service.resumeAsync(
        workflowId, new GenerationResumeService.ResumePlan(projectId, "chk-1", "answers"), true);

    ArgumentCaptor<GenerationClarification> cap =
        ArgumentCaptor.forClass(GenerationClarification.class);
    verify(clarificationRepo).save(cap.capture());
    assertThat(cap.getValue().getRound()).isEqualTo(2);
    assertThat(cap.getValue().getCheckpointId()).isEqualTo("chk-2");
    assertThat(wf.getStatus()).isEqualTo("AWAITING_INPUT");
  }

  @Test
  void allowMoreQuestionsFalseAtCap() {
    when(runRepo.findByWorkflowId(workflowId)).thenReturn(Optional.empty()); // default cap 3
    lenient().when(clarificationRepo.countByWorkflowId(workflowId)).thenReturn(3L);
    assertThat(service.allowMoreQuestions(workflowId)).isFalse();
  }
}
