package com.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.NodeRegistry;
import com.platform.agent.hub.ReviewGateway;
import com.platform.agent.hub.TaskRouter;
import com.platform.agent.hub.graph.TokenBudgetGuard;
import com.platform.agent.node.AgentNode;
import com.platform.common.agent.*;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.AgentWorkflowStep;
import com.platform.core.domain.GenerationClarification;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.AgentWorkflowStepRepository;
import com.platform.core.repository.GenerationClarificationRepository;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates the full workflow lifecycle: trigger → plan → dispatch → review → complete. Nodes are
 * dispatched asynchronously; their results feed back via NodeResult.
 */
@Service
public class AgentWorkflowService {

  private static final Logger log = LoggerFactory.getLogger(AgentWorkflowService.class);

  private final AgentWorkflowRepository workflowRepo;
  private final AgentWorkflowStepRepository stepRepo;
  private final TaskRouter router;
  private final NodeRegistry registry;
  private final ReviewGateway reviewGateway;
  private final TokenBudgetGuard budgetGuard;
  private final List<AgentNode> nodes;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;
  private final GenerationClarificationRepository clarificationRepo;

  public AgentWorkflowService(
      AgentWorkflowRepository workflowRepo,
      AgentWorkflowStepRepository stepRepo,
      TaskRouter router,
      NodeRegistry registry,
      ReviewGateway reviewGateway,
      TokenBudgetGuard budgetGuard,
      List<AgentNode> nodes,
      KafkaTemplate<String, String> kafka,
      ObjectMapper mapper,
      GenerationClarificationRepository clarificationRepo) {
    this.workflowRepo = workflowRepo;
    this.stepRepo = stepRepo;
    this.router = router;
    this.registry = registry;
    this.reviewGateway = reviewGateway;
    this.budgetGuard = budgetGuard;
    this.nodes = nodes;
    this.kafka = kafka;
    this.mapper = mapper;
    this.clarificationRepo = clarificationRepo;
  }

  @Transactional
  public AgentWorkflow createWorkflow(UUID projectId, TriggerRef trigger) {
    AgentWorkflow workflow =
        new AgentWorkflow(
            projectId,
            trigger.triggerType().name(),
            trigger.source() != null ? trigger.source().name() : null,
            Map.of(
                "entityType", String.valueOf(trigger.entityType()),
                "refUrl", String.valueOf(trigger.refUrl())));
    workflowRepo.save(workflow);
    log.info("workflow created: {} trigger={}", workflow.getId(), trigger.triggerType());
    publishEvent(workflow.getId(), "CREATED");
    return workflow;
  }

  @Async
  public void executeWorkflow(UUID workflowId, ContextBundle bundle) {
    AgentWorkflow workflow =
        workflowRepo
            .findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("workflow not found: " + workflowId));

    workflow.markRunning();
    workflowRepo.save(workflow);
    publishEvent(workflowId, "RUNNING");

    if (!budgetGuard.isWithinBudget(bundle.projectId())) {
      workflow.markFailed("Monthly token budget exceeded for project " + bundle.projectId());
      workflowRepo.save(workflow);
      publishEvent(workflowId, "FAILED");
      return;
    }

    List<TaskRouter.TaskAssignment> plan = router.plan(bundle);
    if (plan.isEmpty()) {
      workflow.markFailed("No nodes available for tasks: " + bundle.taskTypes());
      workflowRepo.save(workflow);
      publishEvent(workflowId, "FAILED");
      return;
    }

    try {
      for (TaskRouter.TaskAssignment assignment : plan) {
        AgentNode node = findNode(assignment.taskType());
        if (node == null) {
          log.warn("no node implementation for task {}", assignment.taskType());
          continue;
        }

        AgentWorkflowStep step =
            new AgentWorkflowStep(
                workflowId,
                assignment.nodeId(),
                node.nodeType().name(),
                node.taskType().name(),
                assignment.sequenceOrder());
        stepRepo.save(step);
        step.markRunning();
        stepRepo.save(step);
        publishEvent(workflowId, "NODE_STARTED");

        // Invoke the node's own entrypoint. Simple nodes delegate to orchestrator.run(bundle,this);
        // rich nodes (TestCaseGenerationNode, AutomationCodeGenerationNode) load context and
        // persist
        // their artifacts here — calling orchestrator.run directly would bypass that logic.
        NodeResult result = node.execute(bundle);

        workflow.addTokens(
            result.tokenUsage().totalInputTokens(),
            result.tokenUsage().outputTokens(),
            result.tokenUsage().effectiveCostCents());
        budgetGuard.recordUsage(bundle.projectId(), result.tokenUsage());

        if (result.needsReview()) {
          step.markAwaitingReview(result.summary());
          stepRepo.save(step);
          workflow.markAwaitingReview();
          workflowRepo.save(workflow);
          reviewGateway.requestReview(result, bundle, step.getId());
          publishEvent(workflowId, "AWAITING_REVIEW");
          return; // pause; resume when decision comes back
        }

        if (result.needsInput()) {
          // The agent asked the user for clarification — persist the round and park the workflow.
          int round = (int) clarificationRepo.countByWorkflowId(workflowId) + 1;
          clarificationRepo.save(
              new GenerationClarification(
                  workflowId, round, result.summary(), result.checkpointId()));
          step.markAwaitingReview(result.summary());
          stepRepo.save(step);
          workflow.markAwaitingInput();
          workflowRepo.save(workflow);
          publishEvent(workflowId, "AWAITING_INPUT");
          return; // pause; resume when the user submits answers
        }

        if (result.hasFailed()) {
          step.markFailed(result.errorCode(), result.errorMessage());
          stepRepo.save(step);
          workflow.markFailed(result.errorMessage());
          workflowRepo.save(workflow);
          publishEvent(workflowId, "FAILED");
          return;
        }

        step.markCompleted(
            result.summary(),
            result.tokenUsage().totalInputTokens(),
            result.tokenUsage().outputTokens(),
            result.tokenUsage().effectiveCostCents());
        stepRepo.save(step);
        publishEvent(workflowId, "NODE_COMPLETED");
      }

      workflow.markCompleted();
      workflowRepo.save(workflow);
      publishEvent(workflowId, "COMPLETED");

    } catch (Exception e) {
      log.error("workflow {} failed with exception", workflowId, e);
      workflow.markFailed(e.getMessage());
      workflowRepo.save(workflow);
      publishEvent(workflowId, "FAILED");
    }
  }

  private AgentNode findNode(AgentTaskType taskType) {
    return nodes.stream().filter(n -> n.taskType() == taskType).findFirst().orElse(null);
  }

  private void publishEvent(UUID workflowId, String eventType) {
    try {
      kafka.send(
          Topics.AGENT_WORKFLOW_EVENTS,
          workflowId.toString(),
          mapper.writeValueAsString(
              Map.of(
                  "workflowId", workflowId.toString(),
                  "event", eventType,
                  "timestamp", java.time.Instant.now().toString())));
    } catch (Exception e) {
      log.warn("failed to publish workflow event {} for {}", eventType, workflowId);
    }
  }
}
