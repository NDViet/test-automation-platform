package com.platform.agent.node.impl;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.agent.*;
import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.common.storage.BlobStoreBuckets;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.agent.node.CheckpointService;
import com.platform.agent.node.StepSummarizer;
import com.platform.core.repository.PlatformSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Drives a Node through a Claude tool-use conversation loop.
 *
 * Flow:
 *   1. Build system prompt from ContextBundle
 *   2. Maintain messages list across turns
 *   3. Call Claude API; if stop_reason=tool_use → execute tools → append results → repeat
 *   4. If stop_reason=end_turn → extract final output, return NodeResult.completed()
 *   5. Nodes can signal AWAITING_REVIEW by including a sentinel in their tool output
 */
@Component
public class ClaudeAgentOrchestrator implements AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAgentOrchestrator.class);
    private static final int MAX_TOOL_ITERATIONS = 25;

    @Value("${anthropic.api-key:}")
    private String envApiKey;

    @Value("${platform.agent.max-tokens:8192}")
    private int maxTokens;

    private static final String DB_KEY_ANTHROPIC = "ai.anthropic.api-key";
    private static final String DB_KEY_LEGACY    = "ai.api-key";

    private final CheckpointService checkpointService;
    private final BlobStore blobStore;
    private final ObjectMapper mapper;
    private final PlatformSettingRepository settingRepo;

    private StepSummarizer stepSummarizer;

    // Cached client — rebuilt only when the resolved key changes
    private volatile String cachedKey = null;
    private volatile AnthropicClient client;

    public ClaudeAgentOrchestrator(CheckpointService checkpointService,
                                    BlobStore blobStore,
                                    ObjectMapper mapper,
                                    PlatformSettingRepository settingRepo) {
        this.checkpointService = checkpointService;
        this.blobStore         = blobStore;
        this.mapper            = mapper;
        this.settingRepo       = settingRepo;
    }

    @Autowired(required = false)
    public void setStepSummarizer(StepSummarizer stepSummarizer) {
        this.stepSummarizer = stepSummarizer;
    }

    @Override
    public NodeResult run(ContextBundle bundle, AgentNode node) {
        AnthropicClient claude = getClient();
        if (claude == null) {
            return NodeResult.failed(bundle.sessionId(), bundle.workflowId(),
                    node.nodeType(), node.taskType(),
                    "MISSING_API_KEY", "ANTHROPIC_API_KEY not configured",
                    TokenUsage.zero());
        }

        String systemPrompt = buildSystemPrompt(bundle, node);
        String userPrompt   = buildUserPrompt(bundle, node);
        Model  model        = resolveModel(bundle.llmTier());

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(userPrompt)
                .build());

        TokenUsage totalUsage = TokenUsage.zero();
        int iterations = 0;

        List<Tool> nodeTools = node.tools();

        try {
            // Strategy A: mark the (large, stable) system prompt for prompt caching.
            // On the first turn it's written to cache (cacheCreationInputTokens); subsequent
            // turns read from cache (cacheReadInputTokens) at ~10% of the normal token cost.
            List<TextBlockParam> systemBlocks = List.of(
                    TextBlockParam.builder()
                            .text(systemPrompt)
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build());

            while (iterations++ < MAX_TOOL_ITERATIONS) {
                MessageCreateParams.Builder reqBuilder = MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .systemOfTextBlockParams(systemBlocks)
                        .messages(messages);
                nodeTools.forEach(reqBuilder::addTool);
                Message response = claude.messages().create(reqBuilder.build());

                // Accumulate token usage
                int inputFresh = (int) response.usage().inputTokens();
                int outputToks = (int) response.usage().outputTokens();
                int cacheWrite = response.usage().cacheCreationInputTokens()
                        .map(Long::intValue).orElse(0);
                int cacheRead  = response.usage().cacheReadInputTokens()
                        .map(Long::intValue).orElse(0);
                totalUsage = totalUsage.add(new TokenUsage(
                        inputFresh, cacheWrite, cacheRead, outputToks,
                        estimateCost(inputFresh, cacheWrite, cacheRead, outputToks, model)));

                // Add assistant turn to history
                List<ContentBlockParam> assistantBlocks = response.content().stream()
                        .map(ContentBlock::toParam)
                        .toList();
                messages.add(MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .contentOfBlockParams(assistantBlocks)
                        .build());

                StopReason stopReason = response.stopReason().orElse(StopReason.END_TURN);

                if (stopReason == StopReason.END_TURN || stopReason == StopReason.MAX_TOKENS) {
                    String finalText = extractText(response);
                    return NodeResult.completed(bundle.sessionId(), bundle.workflowId(),
                            node.nodeType(), node.taskType(),
                            ArtifactManifest.empty(), finalText, totalUsage);
                }

                if (stopReason == StopReason.TOOL_USE) {
                    List<ContentBlockParam> toolResults = new ArrayList<>();
                    boolean needsReview = false;
                    String checkpointId = null;

                    for (ContentBlock block : response.content()) {
                        if (block.isToolUse()) {
                            ToolUseBlock tu = block.asToolUse();
                            String toolResult = dispatchToolCall(
                                    tu.name(), tu._input().toString(), bundle, node);

                            // Compress large tool results before sending back to Claude
                            if (stepSummarizer != null
                                    && toolResult.length() > 800
                                    && !toolResult.startsWith("__AWAITING_REVIEW__")) {
                                toolResult = stepSummarizer.summarize(tu.name(), toolResult);
                            }

                            // Node signals review by returning a special sentinel
                            if (toolResult.startsWith("__AWAITING_REVIEW__")) {
                                needsReview  = true;
                                checkpointId = saveCheckpoint(bundle, messages, totalUsage);
                                toolResult   = toolResult.substring("__AWAITING_REVIEW__".length()).trim();
                            }

                            toolResults.add(ContentBlockParam.ofToolResult(
                                    ToolResultBlockParam.builder()
                                            .toolUseId(tu.id())
                                            .content(toolResult)
                                            .build()));
                        }
                    }

                    if (needsReview) {
                        String summary = extractText(response);
                        return NodeResult.awaitingReview(bundle.sessionId(), bundle.workflowId(),
                                node.nodeType(), node.taskType(),
                                ArtifactManifest.empty(), summary, checkpointId, totalUsage);
                    }

                    messages.add(MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .contentOfBlockParams(toolResults)
                            .build());
                }
            }

            // Max iterations reached — return partial result
            log.warn("max tool iterations ({}) reached for session {}", MAX_TOOL_ITERATIONS, bundle.sessionId());
            return NodeResult.completed(bundle.sessionId(), bundle.workflowId(),
                    node.nodeType(), node.taskType(),
                    ArtifactManifest.empty(), "Reached max iterations", totalUsage);

        } catch (Exception e) {
            log.error("orchestrator error for session {}", bundle.sessionId(), e);
            return NodeResult.failed(bundle.sessionId(), bundle.workflowId(),
                    node.nodeType(), node.taskType(),
                    "CLAUDE_ERROR", e.getMessage(), totalUsage);
        }
    }

    @Override
    public NodeResult resume(String checkpointId, AgentNode node) {
        return checkpointService.load(checkpointId)
                .map(state -> {
                    // Reload conversation from checkpoint and re-run from last state
                    // Full resume implementation builds messages from stored blob
                    log.info("resuming from checkpoint {}", checkpointId);
                    return NodeResult.failed(UUID.randomUUID(), UUID.randomUUID(),
                            node.nodeType(), node.taskType(),
                            "RESUME_NOT_IMPLEMENTED", "Checkpoint resume requires full handoff state",
                            TokenUsage.zero());
                })
                .orElseGet(() -> NodeResult.failed(UUID.randomUUID(), UUID.randomUUID(),
                        node.nodeType(), node.taskType(),
                        "CHECKPOINT_NOT_FOUND", "Checkpoint " + checkpointId + " not found or expired",
                        TokenUsage.zero()));
    }

    // -------------------------------------------------------------------------

    protected String dispatchToolCall(String toolName, String inputJson,
                                       ContextBundle bundle, AgentNode node) {
        log.debug("tool call dispatched: tool={} node={}", toolName, node.nodeType());
        return node.dispatchToolCall(toolName, inputJson, bundle);
    }

    private String buildSystemPrompt(ContextBundle bundle, AgentNode node) {
        String nodePrompt = node.systemPrompt(bundle);
        if (nodePrompt != null) return nodePrompt;

        // Generic fallback for nodes that don't supply their own prompt
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a specialized QA automation agent of type %s.
                Your task: %s
                Project: %s (ID: %s)
                You have access to tools to complete this task. Be precise, concise, and produce structured outputs.
                When you need human review before proceeding, call the request_review tool with your proposed output.
                """.formatted(node.nodeType(), node.taskType(), bundle.projectSlug(), bundle.projectId()));
        return sb.toString();
    }

    private String buildUserPrompt(ContextBundle bundle, AgentNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(node.taskType().name()).append("\n\n");

        if (bundle.hasRequirements() && bundle.requirementContext().target() != null) {
            appendRequirementContext(sb, bundle.requirementContext());
        }
        if (bundle.hasExecutionHistory()) {
            sb.append("Pass rate (7d): ")
              .append(String.format("%.0f%%", bundle.executionContext().passRate7d() * 100)).append("\n");
        }
        if (bundle.releaseVersion() != null) {
            sb.append("Release: ").append(bundle.releaseVersion()).append("\n");
        }
        if (bundle.trigger() != null && bundle.trigger().refUrl() != null) {
            sb.append("Trigger URL: ").append(bundle.trigger().refUrl()).append("\n");
        }
        if (bundle.trigger() != null && bundle.trigger().entityExternalId() != null) {
            sb.append("Entity: ").append(bundle.trigger().entityType()).append(" #")
              .append(bundle.trigger().entityExternalId()).append("\n");
        }
        if (bundle.hasPrDiff()) {
            // Inline the pre-fetched changed files list so Claude doesn't need a tool call for it
            BlobRef ref = bundle.prDiff();
            blobStore.fetchText(ref).ifPresentOrElse(
                    content -> sb.append("\nPR changed files (pre-fetched):\n").append(content).append("\n"),
                    ()      -> sb.append("\nPR diff blob key: ").append(ref.key()).append("\n")
            );
        }
        return sb.toString();
    }

    /**
     * Renders the full requirement hierarchy into the user prompt so Claude understands
     * the Epic→Story→Subtask chain, sibling scope, cross-links, and release context.
     * Format is structured but token-efficient: indented tree, then cross-links, then release tags.
     */
    private void appendRequirementContext(StringBuilder sb, com.platform.common.agent.RequirementContext ctx) {
        var target = ctx.target();

        // ── Target requirement ────────────────────────────────────────────────
        sb.append("\n## Requirement\n");
        sb.append("**[").append(target.issueType()).append("] ")
          .append(target.externalId() != null ? target.externalId() + " — " : "")
          .append(target.title()).append("**\n");
        if (target.description() != null && !target.description().isBlank()) {
            sb.append(target.description().trim()).append("\n");
        }
        if (!target.acceptanceCriteria().isEmpty()) {
            sb.append("\nAcceptance criteria:\n");
            target.acceptanceCriteria().forEach(ac ->
                    sb.append("- ").append(ac.text()).append("\n"));
        }

        // ── Ancestor chain (parent → grandparent → epic) ─────────────────────
        if (!ctx.ancestors().isEmpty()) {
            sb.append("\n## Hierarchy (ancestors)\n");
            int indent = 0;
            // ancestors list is ordered immediate-parent → root; reverse for top-down display
            var reversed = new java.util.ArrayList<>(ctx.ancestors());
            java.util.Collections.reverse(reversed);
            for (var anc : reversed) {
                sb.append("  ".repeat(indent)).append("↳ [").append(anc.issueType()).append("] ");
                if (anc.externalId() != null) sb.append(anc.externalId()).append(" — ");
                sb.append(anc.title()).append("\n");
                if (anc.description() != null && !anc.description().isBlank()) {
                    sb.append("  ".repeat(indent + 1)).append(anc.description().trim()).append("\n");
                }
                indent++;
            }
            // Target sits at deepest indent
            sb.append("  ".repeat(indent)).append("→ **THIS: ").append(target.title()).append("**\n");
        }

        // ── Children (subtasks / stories under an epic) ───────────────────────
        if (!ctx.children().isEmpty()) {
            sb.append("\n## Children (").append(ctx.children().size()).append(")\n");
            ctx.children().forEach(ch -> {
                sb.append("- [").append(ch.issueType()).append("] ");
                if (ch.externalId() != null) sb.append(ch.externalId()).append(" — ");
                sb.append(ch.title()).append("\n");
            });
        }

        // ── Cross-links ───────────────────────────────────────────────────────
        if (!ctx.links().isEmpty()) {
            sb.append("\n## Related requirements\n");
            ctx.links().forEach(lk -> {
                String rel = lk.linkSubtype() != null
                        ? lk.linkSubtype().name().toLowerCase().replace('_', ' ')
                        : lk.edgeType().name().toLowerCase().replace('_', ' ');
                sb.append("- ").append(rel).append(": [").append(lk.targetExternalId()).append("] ")
                  .append(lk.targetTitle()).append("\n");
            });
        }

        // ── Release / sprint scope ────────────────────────────────────────────
        if (!ctx.releaseScope().isEmpty()) {
            sb.append("\nRelease scope: ").append(String.join(", ", ctx.releaseScope())).append("\n");
        }

        sb.append("\n");
    }

    private String extractText(Message response) {
        if (response == null) return "";
        return response.content().stream()
                .filter(ContentBlock::isText)
                .map(b -> b.asText().text())
                .findFirst()
                .orElse("(no text output)");
    }

    private String saveCheckpoint(ContextBundle bundle, List<MessageParam> messages, TokenUsage usage) {
        try {
            String messagesJson = mapper.writeValueAsString(messages);
            var blob = blobStore.storeText(BlobStoreBuckets.CHECKPOINTS, messagesJson,
                    com.platform.common.storage.BlobRef.TYPE_JSON);
            var state = new CheckpointService.ConversationState(
                    bundle.sessionId().toString(), blob, null, null,
                    List.of(),
                    bundle.resumeStrategy() != null ? bundle.resumeStrategy() : ResumeStrategy.COMPRESSED,
                    java.time.Instant.now());
            return checkpointService.save(bundle, state, state.strategy());
        } catch (Exception e) {
            log.error("failed to save checkpoint", e);
            return UUID.randomUUID().toString();
        }
    }

    private Model resolveModel(LlmTier tier) {
        return tier == LlmTier.COMPLEX ? Model.CLAUDE_OPUS_4_6 : Model.CLAUDE_SONNET_4_6;
    }

    private BigDecimal estimateCost(int inputFresh, int cacheWrite, int cacheRead,
                                     int output, Model model) {
        // Rough per-token estimates in cents (1/100 of a cent per token approximation)
        double inputRate  = model == Model.CLAUDE_OPUS_4_6 ? 0.0015 : 0.0003;
        double outputRate = model == Model.CLAUDE_OPUS_4_6 ? 0.0075 : 0.0015;
        double cost = (inputFresh * inputRate) + (output * outputRate);
        return BigDecimal.valueOf(cost).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private String resolveApiKey() {
        // 1. Provider-specific DB key (set via AI Settings page)
        String dbKey = settingRepo.findById(DB_KEY_ANTHROPIC)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
        if (dbKey != null) return dbKey;
        // 2. Legacy shared DB key
        String legacyKey = settingRepo.findById(DB_KEY_LEGACY)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
        if (legacyKey != null) return legacyKey;
        // 3. Startup environment variable
        if (envApiKey != null && !envApiKey.isBlank()) return envApiKey;
        return null;
    }

    private synchronized AnthropicClient getClient() {
        String key = resolveApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!key.equals(cachedKey)) {
            log.info("[Agent] Anthropic API key changed — rebuilding client");
            cachedKey = key;
            client = AnthropicOkHttpClient.builder().apiKey(key).build();
        }
        return client;
    }
}
