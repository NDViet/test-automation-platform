package com.platform.agent.api;

/** Create/update payload for an AI skill. {@code enabled} defaults to true when null. */
public record AiSkillRequest(
    String name, String description, String instructions, Boolean enabled) {

  public boolean enabledOrDefault() {
    return enabled == null || enabled;
  }
}
