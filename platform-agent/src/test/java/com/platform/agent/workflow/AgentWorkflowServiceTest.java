package com.platform.agent.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.contract.AgentGridFixtures;
import com.platform.agent.hub.NodeRegistry;
import com.platform.agent.hub.ReviewGateway;
import com.platform.agent.hub.TaskRouter;
import com.platform.agent.hub.graph.TokenBudgetGuard;
import com.platform.agent.node.AgentNode;
import com.platform.common.agent.AgentTaskType;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.NodeResult;
import com.platform.common.agent.NodeType;
import com.platform.common.agent.TokenUsage;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.GenerationClarification;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.AgentWorkflowStepRepository;
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

@ExtendWith(MockitoExtension.class)
class AgentWorkflowServiceTest {

  @Mock AgentWorkflowRepository workflowRepo;
  @Mock AgentWorkflowStepRepository stepRepo;
  @Mock TaskRouter router;
  @Mock NodeRegistry registry;
  @Mock ReviewGateway reviewGateway;
  @Mock TokenBudgetGuard budgetGuard;
  @Mock KafkaTemplate<String, String> kafka;
  @Mock GenerationClarificationRepository clarificationRepo;

  AgentWorkflowService service;
  private final UUID workflowId = UUID.randomUUID();

  /** Result the stub node returns from execute(); set per-test before executeWorkflow. */
  private NodeResult nodeResult;

  private AgentNode genNode() {
    return new AgentNode() {
      @Override
      public AgentTaskType taskType() {
        return AgentTaskType.GENERATE_TEST_CASES;
      }

      @Override
      public NodeType nodeType() {
        return NodeType.TEST_GENERATION;
      }

      @Override
      public NodeResult execute(ContextBundle bundle) {
        return nodeResult;
      }
    };
  }

  @BeforeEach
  void setUp() {
    service =
        new AgentWorkflowService(
            workflowRepo,
            stepRepo,
            router,
            registry,
            reviewGateway,
            budgetGuard,
            List.of(genNode()),
            kafka,
            new ObjectMapper(),
            clarificationRepo);
  }

  @Test
  void awaitingInputPersistsClarificationAndParksWorkflow() {
    AgentWorkflow workflow =
        new AgentWorkflow(UUID.randomUUID(), "MANUAL", null, java.util.Map.of());
    when(workflowRepo.findById(workflowId)).thenReturn(Optional.of(workflow));
    when(budgetGuard.isWithinBudget(any())).thenReturn(true);
    when(router.plan(any()))
        .thenReturn(
            List.of(
                new TaskRouter.TaskAssignment(
                    UUID.randomUUID(), AgentTaskType.GENERATE_TEST_CASES, 0)));
    ContextBundle bundle = AgentGridFixtures.bundle();
    String questions = "[{\"id\":\"q1\",\"question\":\"Which browsers?\"}]";
    nodeResult =
        NodeResult.awaitingInput(
            bundle.sessionId(),
            workflowId,
            NodeType.TEST_GENERATION,
            AgentTaskType.GENERATE_TEST_CASES,
            questions,
            "chk-1",
            TokenUsage.zero());

    service.executeWorkflow(workflowId, bundle);

    ArgumentCaptor<GenerationClarification> cap =
        ArgumentCaptor.forClass(GenerationClarification.class);
    verify(clarificationRepo).save(cap.capture());
    assertThat(cap.getValue().getRound()).isEqualTo(1);
    assertThat(cap.getValue().getQuestionsJson()).isEqualTo(questions);
    assertThat(cap.getValue().getCheckpointId()).isEqualTo("chk-1");
    assertThat(cap.getValue().getStatus()).isEqualTo("PENDING");
    assertThat(workflow.getStatus()).isEqualTo("AWAITING_INPUT");
    verify(reviewGateway, never()).requestReview(any(), any(), any());
  }
}
