package com.platform.agent.contract;

import com.platform.common.agent.*;
import com.platform.common.integration.IntegrationType;
import com.platform.common.storage.BlobRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared test fixtures for agent grid contract tests. All builders produce valid, fully-populated
 * objects so individual tests only need to override the specific fields they care about.
 */
public final class AgentGridFixtures {

  private AgentGridFixtures() {}

  public static final UUID PROJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  public static final UUID WORKFLOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  public static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  public static final UUID REQUIREMENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  public static final UUID TEST_CASE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

  /** Minimal ContextBundle for unit testing (no Claude API call needed). */
  public static ContextBundle bundle() {
    return bundle(webhookTrigger());
  }

  public static ContextBundle bundle(TriggerRef trigger) {
    return new ContextBundle(
        SESSION_ID,
        WORKFLOW_ID,
        PROJECT_ID,
        "test-project",
        List.of(AgentTaskType.ANALYZE_PR_DIFF),
        trigger,
        null,
        null,
        null,
        executionContext(),
        null,
        null,
        null,
        null,
        null,
        ResumeStrategy.COMPRESSED,
        null,
        LlmTier.STANDARD,
        Instant.now());
  }

  public static ContextBundle bundleWithRequirementContext(RequirementContext reqCtx) {
    return new ContextBundle(
        SESSION_ID,
        WORKFLOW_ID,
        PROJECT_ID,
        "test-project",
        List.of(AgentTaskType.GENERATE_AUTOMATED_TESTS),
        manualTrigger("REQ-42"),
        reqCtx,
        null,
        null,
        executionContext(),
        null,
        null,
        null,
        null,
        null,
        ResumeStrategy.COMPRESSED,
        null,
        LlmTier.STANDARD,
        Instant.now());
  }

  public static TriggerRef webhookTrigger() {
    return new TriggerRef(
        TriggerRef.TriggerType.WEBHOOK,
        IntegrationType.GITHUB,
        "pull_request",
        "123",
        "https://github.com/acme/repo/pull/123",
        "bot",
        Instant.now());
  }

  public static TriggerRef manualTrigger(String entityExternalId) {
    return new TriggerRef(
        TriggerRef.TriggerType.MANUAL,
        IntegrationType.JIRA_CLOUD,
        "issue",
        entityExternalId,
        null,
        "user",
        Instant.now());
  }

  public static TriggerRef scheduleTrigger() {
    return new TriggerRef(
        TriggerRef.TriggerType.SCHEDULE,
        null,
        "schedule",
        "nightly",
        null,
        "scheduler",
        Instant.now());
  }

  public static ExecutionContext executionContext() {
    return new ExecutionContext(
        UUID.randomUUID(), Instant.now().minusSeconds(3600), 0.85, 0.10, 0, List.of(), "default");
  }

  public static TokenUsage tokenUsage(int input, int output) {
    return new TokenUsage(
        input, 0, 0, output, java.math.BigDecimal.valueOf(input * 0.0003 + output * 0.0015));
  }

  public static BlobRef blobRef(String key) {
    return new BlobRef("diffs", key, key, BlobRef.TYPE_JSON, 1024L);
  }
}
