package com.platform.agent.proposals;

import java.util.List;
import java.util.UUID;

/** Request/response shapes for the generated-test-case proposal review API. */
public final class ProposalDtos {

  private ProposalDtos() {}

  public record StepDto(String action, String expectedResult, String notes) {}

  public record ProposalDto(
      UUID id,
      int ordinal,
      String title,
      String description,
      String preconditions,
      String expectedResult,
      String priority,
      String sourceRequirementId,
      String status,
      UUID acceptedTestCaseId,
      List<StepDto> steps) {}

  /** A free-text instruction for refining a proposal (or all proposals). */
  public record RefineRequest(String instruction) {}
}
