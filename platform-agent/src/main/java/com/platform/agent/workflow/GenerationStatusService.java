package com.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.GenerationClarification;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.GenerationClarificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read model for a generation run: its workflow status, the full clarification transcript, and the
 * currently pending question round (if the run is awaiting input).
 */
@Service
public class GenerationStatusService {

  private final AgentWorkflowRepository workflowRepo;
  private final GenerationClarificationRepository clarificationRepo;
  private final ObjectMapper mapper;

  public GenerationStatusService(
      AgentWorkflowRepository workflowRepo,
      GenerationClarificationRepository clarificationRepo,
      ObjectMapper mapper) {
    this.workflowRepo = workflowRepo;
    this.clarificationRepo = clarificationRepo;
    this.mapper = mapper;
  }

  public record RoundDto(int round, String status, Object questions, Object answers) {}

  public record GenerationStatusDto(
      String workflowId, String status, List<RoundDto> rounds, RoundDto pending) {}

  @Transactional(readOnly = true)
  public GenerationStatusDto getStatus(UUID projectId, UUID workflowId) {
    AgentWorkflow workflow =
        workflowRepo
            .findById(workflowId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    if (!workflow.getProjectId().equals(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
    }

    List<RoundDto> rounds =
        clarificationRepo.findByWorkflowIdOrderByRoundAsc(workflowId).stream()
            .map(this::toRound)
            .toList();
    RoundDto pending =
        rounds.stream().filter(r -> "PENDING".equals(r.status())).reduce((a, b) -> b).orElse(null);

    return new GenerationStatusDto(workflowId.toString(), workflow.getStatus(), rounds, pending);
  }

  private RoundDto toRound(GenerationClarification c) {
    return new RoundDto(
        c.getRound(), c.getStatus(), parse(c.getQuestionsJson()), parse(c.getAnswersJson()));
  }

  private Object parse(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      return mapper.readTree(json);
    } catch (Exception e) {
      return json;
    }
  }
}
