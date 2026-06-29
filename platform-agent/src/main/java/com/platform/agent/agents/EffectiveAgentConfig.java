package com.platform.agent.agents;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fully-resolved configuration for running a task: the agent picked by {@link
 * AgentResolutionService} (or the built-in seed), with its prompts composed and references
 * resolved. Consumed by the task node — it should not need to look anything else up.
 */
public record EffectiveAgentConfig(
    Source source,
    UUID agentId, // null for a seed
    String systemPrompt, // composed: persona + system body + applied skills
    String userPrompt,
    String modelId, // explicit LiteLLM model id, or null
    String modelRole, // STANDARD | COMPLEX | SUMMARIZER, or null ⇒ task default tier
    List<UUID> skillIds, // resolved, visible, enabled
    Map<String, Object> contextConfig,
    int maxRounds) {

  /** Where the resolved agent came from in the cascade. */
  public enum Source {
    PROJECT,
    ORG,
    SEED
  }
}
