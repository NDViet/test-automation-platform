package com.platform.agent.api;

/** Create/update payload for a prompt template. */
public record AiPromptTemplateRequest(String kind, String name, String body, Boolean isDefault) {

  public boolean isDefaultOrFalse() {
    return isDefault != null && isDefault;
  }
}
