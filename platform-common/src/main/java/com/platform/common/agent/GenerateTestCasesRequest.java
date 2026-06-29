package com.platform.common.agent;

import java.util.List;

/**
 * Rich AI test-case generation request. Any combination of requirements, free text, and uploaded
 * files may be supplied as input (at least one is required); skills and per-run prompt overrides
 * steer the agent. Backward compatible: a body with only {@code requirementIds} behaves like the
 * original one-shot flow.
 */
public record GenerateTestCasesRequest(
    List<String> requirementIds,
    String freeText,
    List<String> fileIds,
    List<String> skillIds,
    String systemPromptOverride,
    String userPromptOverride,
    Integer maxRounds) {

  public List<String> requirementIdsOrEmpty() {
    return requirementIds == null ? List.of() : requirementIds;
  }

  public List<String> fileIdsOrEmpty() {
    return fileIds == null ? List.of() : fileIds;
  }

  public List<String> skillIdsOrEmpty() {
    return skillIds == null ? List.of() : skillIds;
  }

  public boolean hasFreeText() {
    return freeText != null && !freeText.isBlank();
  }

  /** True when at least one input source (requirements, free text, or files) is present. */
  public boolean hasAnyInput() {
    return !requirementIdsOrEmpty().isEmpty() || hasFreeText() || !fileIdsOrEmpty().isEmpty();
  }
}
