package com.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.AiGenerationRunRepository;
import com.platform.core.repository.ImpactAnalysisRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the PR context available to a parked generation workflow by reusing stored Impact
 * Analyses: an analysis links PR(s) to requirement id(s), so the analyses whose requirements
 * intersect the workflow's requirement set are the PR context a tester can inject into a
 * regeneration round. No live GitHub fetching — this is a read over what's already been analysed.
 *
 * <p>The workflow's requirement set is read from its {@link
 * com.platform.core.domain.AiGenerationRun} (persisted at generation time), because the trigger's
 * entity id is not reconstructed on resume. When the run generated for all requirements (empty set)
 * every analysis with PRs is offered.
 */
@Service
public class PrContextService {

  private static final Logger log = LoggerFactory.getLogger(PrContextService.class);

  private final AgentWorkflowRepository workflowRepo;
  private final AiGenerationRunRepository runRepo;
  private final ImpactAnalysisRepository impactRepo;
  private final ObjectMapper mapper;

  public PrContextService(
      AgentWorkflowRepository workflowRepo,
      AiGenerationRunRepository runRepo,
      ImpactAnalysisRepository impactRepo,
      ObjectMapper mapper) {
    this.workflowRepo = workflowRepo;
    this.runRepo = runRepo;
    this.impactRepo = impactRepo;
    this.mapper = mapper;
  }

  /**
   * One selectable PR-context source (an Impact Analysis) for the Review-Queue regenerate panel.
   */
  public record PrContextItem(
      String analysisId, String name, List<Map<String, Object>> prs, String summary) {}

  @Transactional(readOnly = true)
  public List<PrContextItem> forWorkflow(UUID projectId, UUID workflowId) {
    AgentWorkflow workflow =
        workflowRepo
            .findById(workflowId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    if (!workflow.getProjectId().equals(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
    }

    Set<String> requirementIds = requirementIdSet(workflowId);

    return impactRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .filter(a -> a.getLinkedPrs() != null && !a.getLinkedPrs().isEmpty())
        .filter(a -> requirementIds.isEmpty() || intersects(a, requirementIds))
        .map(
            a ->
                new PrContextItem(
                    a.getId().toString(), a.getName(), a.getLinkedPrs(), a.getSummary()))
        .toList();
  }

  /**
   * The workflow's effective requirement id set, or empty when it generated for all requirements.
   */
  Set<String> requirementIdSet(UUID workflowId) {
    String json =
        runRepo.findByWorkflowId(workflowId).map(r -> r.getRequirementIdsJson()).orElse(null);
    if (json == null || json.isBlank()) return Set.of();
    try {
      List<String> ids =
          mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
      return new HashSet<>(ids);
    } catch (Exception e) {
      log.debug(
          "Could not parse requirement id set for workflow {}: {}", workflowId, e.getMessage());
      return Set.of();
    }
  }

  private static boolean intersects(ImpactAnalysis a, Set<String> requirementIds) {
    List<String> linked = a.getLinkedRequirementIds();
    if (linked == null) return false;
    return linked.stream().anyMatch(requirementIds::contains);
  }
}
