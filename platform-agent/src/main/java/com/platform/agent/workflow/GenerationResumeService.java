package com.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.node.impl.TestCaseGenerationNode;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.NodeResult;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.GenerationClarification;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.AiGenerationRunRepository;
import com.platform.core.repository.GenerationClarificationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resumes a parked generation workflow after the user answers clarifying questions. The answer
 * submission is validated + recorded synchronously (so the caller gets 409 on a bad state), then the
 * conversation is resumed asynchronously from its checkpoint. Multi-round is supported up to the
 * run's {@code maxRounds} cap; at the cap the agent is told to proceed with best effort.
 */
@Service
public class GenerationResumeService {

  private static final Logger log = LoggerFactory.getLogger(GenerationResumeService.class);

  private final AgentWorkflowRepository workflowRepo;
  private final GenerationClarificationRepository clarificationRepo;
  private final AiGenerationRunRepository runRepo;
  private final ContextAssembler contextAssembler;
  private final TestCaseGenerationNode generationNode;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;

  public GenerationResumeService(
      AgentWorkflowRepository workflowRepo,
      GenerationClarificationRepository clarificationRepo,
      AiGenerationRunRepository runRepo,
      ContextAssembler contextAssembler,
      TestCaseGenerationNode generationNode,
      KafkaTemplate<String, String> kafka,
      ObjectMapper mapper) {
    this.workflowRepo = workflowRepo;
    this.clarificationRepo = clarificationRepo;
    this.runRepo = runRepo;
    this.contextAssembler = contextAssembler;
    this.generationNode = generationNode;
    this.kafka = kafka;
    this.mapper = mapper;
  }

  /** A single answer to a clarifying question. */
  public record Answer(String id, String answer) {}

  /** What the async resume needs after answers are recorded. */
  public record ResumePlan(UUID projectId, String checkpointId, String answersText) {}

  /**
   * Validate state, mark the pending clarification answered, and flip the workflow to RUNNING so a
   * duplicate submission is rejected. Returns the plan the async resume will execute.
   */
  @Transactional
  public ResumePlan markAnswered(UUID projectId, UUID workflowId, List<Answer> answers) {
    AgentWorkflow workflow =
        workflowRepo
            .findById(workflowId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    if (!workflow.getProjectId().equals(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
    }
    if (!"AWAITING_INPUT".equals(workflow.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run is not awaiting input (status " + workflow.getStatus() + ")");
    }
    GenerationClarification clar =
        clarificationRepo
            .findFirstByWorkflowIdAndStatusOrderByRoundDesc(workflowId, "PENDING")
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.CONFLICT, "No pending clarification to answer"));

    clar.markAnswered(writeJson(answers));
    clarificationRepo.save(clar);
    workflow.markRunning();
    workflowRepo.save(workflow);

    int maxRounds = runRepo.findByWorkflowId(workflowId).map(r -> r.getMaxRounds()).orElse(3);
    long roundsUsed = clarificationRepo.countByWorkflowId(workflowId);
    boolean atCap = roundsUsed >= maxRounds;
    String answersText = formatAnswers(answers, atCap);
    return new ResumePlan(projectId, clar.getCheckpointId(), answersText);
  }

  /** Resume the conversation from the checkpoint and handle whatever the model returns. */
  @Async
  public void resumeAsync(UUID workflowId, ResumePlan plan, boolean allowMoreQuestions) {
    try {
      ContextBundle bundle = contextAssembler.resume(workflowId, plan.checkpointId());
      NodeResult result =
          generationNode.resumeWithAnswers(
              bundle, plan.checkpointId(), plan.answersText(), allowMoreQuestions);
      handleResult(workflowId, result);
    } catch (Exception e) {
      log.error("resume failed for workflow {}", workflowId, e);
      workflowRepo
          .findById(workflowId)
          .ifPresent(
              w -> {
                w.markFailed(e.getMessage());
                workflowRepo.save(w);
                publishEvent(workflowId, "FAILED");
              });
    }
  }

  /**
   * Whether another clarification round is permitted (used by the caller to allow the agent to ask
   * again). False once the cap is reached, forcing best-effort generation.
   */
  @Transactional(readOnly = true)
  public boolean allowMoreQuestions(UUID workflowId) {
    int maxRounds = runRepo.findByWorkflowId(workflowId).map(r -> r.getMaxRounds()).orElse(3);
    return clarificationRepo.countByWorkflowId(workflowId) < maxRounds;
  }

  private void handleResult(UUID workflowId, NodeResult result) {
    AgentWorkflow workflow = workflowRepo.findById(workflowId).orElse(null);
    if (workflow == null) return;

    if (result.needsInput()) {
      int round = (int) clarificationRepo.countByWorkflowId(workflowId) + 1;
      clarificationRepo.save(
          new GenerationClarification(
              workflowId, round, result.summary(), result.checkpointId()));
      workflow.markAwaitingInput();
      workflowRepo.save(workflow);
      publishEvent(workflowId, "AWAITING_INPUT");
      return;
    }
    if (result.hasFailed()) {
      workflow.markFailed(result.errorMessage());
      workflowRepo.save(workflow);
      publishEvent(workflowId, "FAILED");
      return;
    }
    workflow.markCompleted();
    workflowRepo.save(workflow);
    publishEvent(workflowId, "COMPLETED");
  }

  private String formatAnswers(List<Answer> answers, boolean atCap) {
    StringBuilder sb = new StringBuilder("The user answered your questions:\n");
    for (Answer a : answers) {
      sb.append("- ").append(a.id()).append(": ").append(a.answer()).append("\n");
    }
    if (atCap) {
      sb.append(
          "\nDo NOT ask further questions. Proceed now and generate the test cases with best"
              + " effort, noting any assumptions in the test case descriptions.");
    }
    return sb.toString();
  }

  private String writeJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      return "[]";
    }
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
