package com.platform.agent.proposals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.proposals.ProposalDtos.ProposalDto;
import com.platform.agent.proposals.ProposalDtos.StepDto;
import com.platform.core.domain.GeneratedTestCaseProposal;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseStep;
import com.platform.core.repository.GeneratedTestCaseProposalRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseStepRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Curation of AI-generated test-case proposals before they enter the catalog: list / accept /
 * reject / refine. Proposals are scoped to a project (the endpoints are gated to {@code
 * OPERATE_QUALITY} on that project), so every lookup is project-scoped to prevent cross-project
 * access.
 */
@Service
public class ProposalService {

  private final GeneratedTestCaseProposalRepository proposalRepo;
  private final PlatformTestCaseRepository testCaseRepo;
  private final TestCaseStepRepository stepRepo;
  private final ObjectMapper mapper;

  public ProposalService(
      GeneratedTestCaseProposalRepository proposalRepo,
      PlatformTestCaseRepository testCaseRepo,
      TestCaseStepRepository stepRepo,
      ObjectMapper mapper) {
    this.proposalRepo = proposalRepo;
    this.testCaseRepo = testCaseRepo;
    this.stepRepo = stepRepo;
    this.mapper = mapper;
  }

  /** All proposals for a generation run, ordered, restricted to the given project. */
  @Transactional(readOnly = true)
  public List<ProposalDto> list(UUID projectId, UUID workflowId) {
    return proposalRepo.findByWorkflowIdOrderByOrdinalAsc(workflowId).stream()
        .filter(p -> p.getProjectId().equals(projectId))
        .map(this::toDto)
        .toList();
  }

  /**
   * Accept a proposal: create the catalog test case (DRAFT) + its steps and mark the proposal
   * ACCEPTED. Idempotent (an already-accepted proposal is a no-op); a rejected proposal can't be
   * accepted.
   */
  @Transactional
  public ProposalDto accept(UUID projectId, UUID proposalId, String actor) {
    GeneratedTestCaseProposal p =
        proposalRepo.findByIdAndProjectId(proposalId, projectId).orElseThrow(this::notFound);
    return acceptEntity(p, actor);
  }

  /** Accept every still-PROPOSED proposal in the run. */
  @Transactional
  public List<ProposalDto> acceptAll(UUID projectId, UUID workflowId, String actor) {
    return proposalRepo
        .findByWorkflowIdAndStatusOrderByOrdinalAsc(workflowId, GeneratedTestCaseProposal.PROPOSED)
        .stream()
        .filter(p -> p.getProjectId().equals(projectId))
        .map(p -> acceptEntity(p, actor))
        .toList();
  }

  private ProposalDto acceptEntity(GeneratedTestCaseProposal p, String actor) {
    if (GeneratedTestCaseProposal.ACCEPTED.equals(p.getStatus())) {
      return toDto(p); // idempotent — already in the catalog
    }
    if (!p.isProposed()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot accept a " + p.getStatus().toLowerCase() + " proposal");
    }
    UUID projectId = p.getProjectId();

    PlatformTestCase tc =
        new PlatformTestCase(
            projectId, p.getTitle(), List.of(), actor != null ? actor : "AGENT", p.getWorkflowId());
    tc.setDescription(p.getDescription());
    tc.setPreconditions(p.getPreconditions());
    tc.setExpectedResult(p.getExpectedResult());
    tc.setPriority(p.getPriority());
    if (p.getSourceRequirementId() != null) {
      try {
        UUID reqId = UUID.fromString(p.getSourceRequirementId());
        tc.setSourceRequirementId(reqId);
        tc.linkRequirement(reqId);
      } catch (IllegalArgumentException ignore) {
        // non-UUID source id (free-text generation) — leave unlinked
      }
    }
    tc.setStatus("DRAFT");
    PlatformTestCase saved = testCaseRepo.save(tc);

    int stepNum = 1;
    for (StepDto s : parseSteps(p.getStepsJson())) {
      stepRepo.save(
          new TestCaseStep(saved.getId(), stepNum++, s.action(), s.expectedResult(), s.notes()));
    }

    p.markAccepted(saved.getId());
    return toDto(proposalRepo.save(p));
  }

  /**
   * Reject (discard) a proposal so it never enters the catalog. Idempotent; can't reject one that
   * has already been accepted into the catalog.
   */
  @Transactional
  public ProposalDto reject(UUID projectId, UUID proposalId) {
    GeneratedTestCaseProposal p =
        proposalRepo.findByIdAndProjectId(proposalId, projectId).orElseThrow(this::notFound);
    if (GeneratedTestCaseProposal.REJECTED.equals(p.getStatus())) {
      return toDto(p); // idempotent
    }
    if (GeneratedTestCaseProposal.ACCEPTED.equals(p.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot reject an accepted proposal");
    }
    p.markRejected();
    return toDto(proposalRepo.save(p));
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found");
  }

  private ProposalDto toDto(GeneratedTestCaseProposal p) {
    return new ProposalDto(
        p.getId(),
        p.getOrdinal(),
        p.getTitle(),
        p.getDescription(),
        p.getPreconditions(),
        p.getExpectedResult(),
        p.getPriority(),
        p.getSourceRequirementId(),
        p.getStatus(),
        p.getAcceptedTestCaseId(),
        parseSteps(p.getStepsJson()));
  }

  private List<StepDto> parseSteps(String stepsJson) {
    if (stepsJson == null || stepsJson.isBlank()) return List.of();
    try {
      StepDto[] arr = mapper.readValue(stepsJson, StepDto[].class);
      return List.of(arr);
    } catch (Exception e) {
      return List.of();
    }
  }
}
