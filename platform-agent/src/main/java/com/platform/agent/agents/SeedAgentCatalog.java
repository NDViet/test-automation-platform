package com.platform.agent.agents;

import com.platform.agent.api.AiPromptTemplateService;
import com.platform.common.agent.AgentTaskType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Built-in default ("seed") agent configuration per task type — the resolution fallback used when
 * no org/project agent is assigned. Seeds are code, not DB rows, so there is nothing for a user to
 * delete and the platform always has a working default (mirrors {@link AiPromptTemplateService}'s
 * seed prompts).
 */
@Component
public class SeedAgentCatalog {

  /** A built-in default. {@code modelRole} is null ⇒ the task's existing default tier is used. */
  public record SeedAgent(
      String systemPrompt,
      String userPrompt,
      String modelRole,
      Map<String, Object> contextConfig,
      int maxRounds) {}

  private static final SeedAgent GENERATION_SEED =
      new SeedAgent(
          AiPromptTemplateService.SEED_SYSTEM,
          AiPromptTemplateService.SEED_USER,
          "COMPLEX",
          Map.of("requirements", true, "attachedFiles", true, "existingCoverage", true),
          // 0 rounds ⇒ one-shot, matching today's plain "generate for all requirements" path.
          0);

  private static final SeedAgent GENERIC_SEED =
      new SeedAgent(
          "You are a specialized QA automation agent. Be precise, concise, and produce structured,"
              + " machine-readable output where a format is specified.",
          "Complete the assigned task using the provided context.",
          "STANDARD",
          Map.of(),
          0);

  /** The seed agent for a task type; falls back to a generic default. */
  public SeedAgent seedFor(AgentTaskType taskType) {
    return switch (taskType) {
      case GENERATE_TEST_CASES, GENERATE_MANUAL_TEST_CASES -> GENERATION_SEED;
      default -> GENERIC_SEED;
    };
  }
}
