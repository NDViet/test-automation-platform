package com.platform.agent.node.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.agent.node.CheckpointService;
import com.platform.agent.node.StepSummarizer;
import com.platform.agent.progress.GenerationProgressPublisher;
import com.platform.common.agent.ArtifactManifest;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.LlmTier;
import com.platform.common.agent.NodeResult;
import com.platform.common.agent.ResumeStrategy;
import com.platform.common.agent.TokenUsage;
import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.common.storage.BlobStoreBuckets;
import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Drives a Node through a tool-use loop using a LangChain4j {@link ChatModel} routed through
 * LiteLLM (see {@code platform-llm}). The LiteLLM-backed replacement for the Anthropic-SDK {@code
 * ClaudeAgentOrchestrator}; the model id is chosen per {@link LlmTier} from settings.
 *
 * <p>The loop mirrors the existing one: send messages (+ tool specs) → if the model returns tool
 * calls, dispatch each via {@link AgentNode#dispatchToolCall} and feed results back → repeat until
 * a text turn, the review sentinel, or the {@value #MAX_TOOL_ITERATIONS}-iteration cap.
 *
 * <p>Capability note (SPEC F7): the OpenAI-compatible token usage does not expose Anthropic prompt
 * cache read/write counts, so those columns are 0 until prompt caching is validated against a live
 * LiteLLM. Tool-use and tier model selection are preserved.
 */
@Primary
@Component
public class LangChainAgentRunner implements AgentOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(LangChainAgentRunner.class);
  static final int MAX_TOOL_ITERATIONS = 25;
  private static final String REVIEW_SENTINEL = "__AWAITING_REVIEW__";

  /** A tool dispatch returning this prefix pauses the run for user clarification. */
  static final String INPUT_SENTINEL = "__AWAITING_INPUT__";

  static final String KEY_MODEL_STANDARD = "ai.litellm.model.standard";
  static final String KEY_MODEL_COMPLEX = "ai.litellm.model.complex";
  private static final String DEFAULT_STANDARD = "claude-sonnet-4-6";
  private static final String DEFAULT_COMPLEX = "claude-opus-4-6";

  private final LlmChatModelProvider provider;
  private final LlmSettings settings;
  private final CheckpointService checkpointService;
  private final BlobStore blobStore;
  private final ObjectMapper mapper;
  private final StepSummarizer stepSummarizer;

  /** Optional — present in services that relay progress (generation); absent is fine elsewhere. */
  @Autowired(required = false)
  private GenerationProgressPublisher progressPublisher;

  /** Single daemon thread that enforces the per-token idle timeout on streaming calls. */
  private final ScheduledExecutorService watchdogScheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "llm-stream-watchdog");
            t.setDaemon(true);
            return t;
          });

  public LangChainAgentRunner(
      LlmChatModelProvider provider,
      LlmSettings settings,
      CheckpointService checkpointService,
      BlobStore blobStore,
      ObjectMapper mapper,
      StepSummarizer stepSummarizer) {
    this.provider = provider;
    this.settings = settings;
    this.checkpointService = checkpointService;
    this.blobStore = blobStore;
    this.mapper = mapper;
    this.stepSummarizer = stepSummarizer;
  }

  @Override
  public NodeResult run(ContextBundle bundle, AgentNode node) {
    String modelId = resolveModelId(bundle.llmTier());
    StreamingChatModel model = provider.streamingChatModel(modelId);
    TokenUsage total = TokenUsage.zero();
    if (model == null) {
      return NodeResult.failed(
          bundle.sessionId(),
          bundle.workflowId(),
          node.nodeType(),
          node.taskType(),
          "MISSING_LLM_CONFIG",
          "LiteLLM is not configured — set the base URL and key in AI Settings",
          total);
    }

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(SystemMessage.from(systemPrompt(bundle, node)));
    messages.add(UserMessage.from(userPrompt(bundle, node)));

    if (progressPublisher != null && bundle.workflowId() != null) {
      progressPublisher.started(bundle.workflowId());
    }
    return executeLoop(bundle, node, model, modelId, messages, total);
  }

  @Override
  public NodeResult resume(
      ContextBundle bundle, String checkpointId, AgentNode node, String nextUserMessage) {
    String modelId = resolveModelId(bundle.llmTier());
    StreamingChatModel model = provider.streamingChatModel(modelId);
    if (model == null) {
      return NodeResult.failed(
          bundle.sessionId(),
          bundle.workflowId(),
          node.nodeType(),
          node.taskType(),
          "MISSING_LLM_CONFIG",
          "LiteLLM is not configured — set the base URL and key in AI Settings",
          TokenUsage.zero());
    }

    List<ChatMessage> messages = loadCheckpointMessages(checkpointId);
    if (messages == null) {
      return NodeResult.failed(
          bundle.sessionId(),
          bundle.workflowId(),
          node.nodeType(),
          node.taskType(),
          "CHECKPOINT_NOT_FOUND",
          "Could not load checkpoint " + checkpointId,
          TokenUsage.zero());
    }
    if (nextUserMessage != null && !nextUserMessage.isBlank()) {
      messages.add(UserMessage.from(nextUserMessage));
    }
    if (progressPublisher != null && bundle.workflowId() != null) {
      progressPublisher.started(bundle.workflowId());
    }
    return executeLoop(bundle, node, model, modelId, messages, TokenUsage.zero());
  }

  /** Shared tool-use loop used by both a fresh run and a resumed conversation. */
  private NodeResult executeLoop(
      ContextBundle bundle,
      AgentNode node,
      StreamingChatModel model,
      String modelId,
      List<ChatMessage> messages,
      TokenUsage total) {
    List<ToolSpecification> tools = node.toolSpecs();

    try {
      for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
        ChatRequest.Builder rb = ChatRequest.builder().messages(messages);
        if (tools != null && !tools.isEmpty()) {
          rb.toolSpecifications(tools);
        }
        ChatResponse response = streamChat(model, rb.build(), bundle.workflowId());
        total = total.add(usageOf(response, modelId));
        AiMessage ai = response.aiMessage();

        if (!ai.hasToolExecutionRequests()) {
          String text = ai.text() != null ? ai.text() : "";
          return NodeResult.completed(
              bundle.sessionId(),
              bundle.workflowId(),
              node.nodeType(),
              node.taskType(),
              ArtifactManifest.empty(),
              text,
              total);
        }

        messages.add(ai);
        List<ToolExecutionResultMessage> results = new ArrayList<>();
        for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
          String result = node.dispatchToolCall(req.name(), req.arguments(), bundle);
          if (stepSummarizer != null
              && result.length() > 800
              && !result.startsWith(REVIEW_SENTINEL)
              && !result.startsWith(INPUT_SENTINEL)) {
            result = stepSummarizer.summarize(req.name(), result);
          }
          if (result.startsWith(REVIEW_SENTINEL)) {
            String checkpointId = saveCheckpoint(bundle, messages, total);
            String summary = result.substring(REVIEW_SENTINEL.length()).trim();
            return NodeResult.awaitingReview(
                bundle.sessionId(),
                bundle.workflowId(),
                node.nodeType(),
                node.taskType(),
                ArtifactManifest.empty(),
                summary,
                checkpointId,
                total);
          }
          if (result.startsWith(INPUT_SENTINEL)) {
            // Record the model's question turn so the conversation resumes coherently, then pause.
            messages.add(ToolExecutionResultMessage.from(req, "Awaiting user input."));
            String checkpointId = saveCheckpoint(bundle, messages, total);
            String questionsJson = result.substring(INPUT_SENTINEL.length()).trim();
            return NodeResult.awaitingInput(
                bundle.sessionId(),
                bundle.workflowId(),
                node.nodeType(),
                node.taskType(),
                questionsJson,
                checkpointId,
                total);
          }
          results.add(ToolExecutionResultMessage.from(req, result));
        }
        results.forEach(messages::add);
      }

      log.warn(
          "max tool iterations ({}) reached for session {}",
          MAX_TOOL_ITERATIONS,
          bundle.sessionId());
      return NodeResult.completed(
          bundle.sessionId(),
          bundle.workflowId(),
          node.nodeType(),
          node.taskType(),
          ArtifactManifest.empty(),
          "Reached max iterations",
          total);
    } catch (Exception e) {
      log.error("LangChain agent runner error for session {}", bundle.sessionId(), e);
      return NodeResult.failed(
          bundle.sessionId(),
          bundle.workflowId(),
          node.nodeType(),
          node.taskType(),
          "LLM_ERROR",
          e.getMessage(),
          total);
    }
  }

  /**
   * Stream one chat turn, bridging the async token callback back to a blocking call so the existing
   * tool-use loop is unchanged. Liveness is the token flow: a watchdog aborts only after {@code
   * settings.timeoutSeconds()} of <em>no</em> tokens — so a long-but-progressing generation runs to
   * completion, while a genuinely hung connection is still cut. Throttled previews are relayed to
   * the portal/browser as they arrive.
   */
  private ChatResponse streamChat(StreamingChatModel model, ChatRequest request, UUID workflowId)
      throws Exception {
    int idleTimeoutSeconds = settings.timeoutSeconds();
    CompletableFuture<ChatResponse> future = new CompletableFuture<>();
    AtomicLong lastActivity = new AtomicLong(System.nanoTime());
    AtomicLong lastPublish = new AtomicLong(0L);
    StringBuilder acc = new StringBuilder();

    ScheduledFuture<?> watchdog =
        watchdogScheduler.scheduleAtFixedRate(
            () -> {
              long idleMs = (System.nanoTime() - lastActivity.get()) / 1_000_000L;
              if (!future.isDone() && idleMs > idleTimeoutSeconds * 1000L) {
                future.completeExceptionally(
                    new TimeoutException(
                        "LLM stream idle for "
                            + idleMs
                            + "ms (no tokens received) — aborting at "
                            + idleTimeoutSeconds
                            + "s idle limit"));
              }
            },
            idleTimeoutSeconds,
            5,
            TimeUnit.SECONDS);

    try {
      model.chat(
          request,
          new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partial) {
              lastActivity.set(System.nanoTime());
              if (partial != null && !partial.isEmpty()) {
                acc.append(partial);
              }
              if (progressPublisher != null && workflowId != null) {
                long now = System.nanoTime();
                // Throttle to ≤ ~2.5 msgs/sec so the relay stays light even on fast streams.
                if (now - lastPublish.get() > 400_000_000L) {
                  lastPublish.set(now);
                  progressPublisher.token(workflowId, tail(acc, 4000), acc.length());
                }
              }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
              future.complete(response);
            }

            @Override
            public void onError(Throwable error) {
              future.completeExceptionally(error);
            }
          });
      return future.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception ex) {
        throw ex;
      }
      throw new RuntimeException(cause);
    } finally {
      watchdog.cancel(false);
    }
  }

  /** Last {@code max} characters of the accumulator — a bounded live-preview tail. */
  private static String tail(StringBuilder sb, int max) {
    int len = sb.length();
    return len <= max ? sb.toString() : sb.substring(len - max);
  }

  /** Load and deserialize the conversation messages for a checkpoint, or null if unavailable. */
  private List<ChatMessage> loadCheckpointMessages(String checkpointId) {
    try {
      var state = checkpointService.load(checkpointId).orElse(null);
      if (state == null || state.messagesBlob() == null) return null;
      String json = blobStore.fetchText(state.messagesBlob()).orElse(null);
      if (json == null) return null;
      return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(json));
    } catch (Exception e) {
      log.error("failed to load checkpoint {}", checkpointId, e);
      return null;
    }
  }

  String resolveModelId(LlmTier tier) {
    return tier == LlmTier.COMPLEX
        ? settings.model(KEY_MODEL_COMPLEX, DEFAULT_COMPLEX)
        : settings.model(KEY_MODEL_STANDARD, DEFAULT_STANDARD);
  }

  private TokenUsage usageOf(ChatResponse response, String modelId) {
    var u = response.tokenUsage();
    int in = u != null && u.inputTokenCount() != null ? u.inputTokenCount() : 0;
    int out = u != null && u.outputTokenCount() != null ? u.outputTokenCount() : 0;
    return new TokenUsage(in, 0, 0, out, estimateCost(in, out, modelId));
  }

  private BigDecimal estimateCost(int input, int output, String modelId) {
    boolean opus = modelId != null && modelId.toLowerCase().contains("opus");
    double inputRate = opus ? 0.0015 : 0.0003;
    double outputRate = opus ? 0.0075 : 0.0015;
    return BigDecimal.valueOf(input * inputRate + output * outputRate);
  }

  private String systemPrompt(ContextBundle bundle, AgentNode node) {
    String nodePrompt = node.systemPrompt(bundle);
    if (nodePrompt != null) return nodePrompt;

    // Generic fallback for nodes that don't supply their own prompt
    StringBuilder sb = new StringBuilder();
    sb.append(
        """
        You are a specialized QA automation agent of type %s.
        Your task: %s
        Project: %s (ID: %s)
        You have access to tools to complete this task. Be precise, concise, and produce structured outputs.
        When you need human review before proceeding, call the request_review tool with your proposed output.
        """
            .formatted(node.nodeType(), node.taskType(), bundle.projectSlug(), bundle.projectId()));
    return sb.toString();
  }

  private String userPrompt(ContextBundle bundle, AgentNode node) {
    StringBuilder sb = new StringBuilder();
    sb.append("Task: ").append(node.taskType().name()).append("\n\n");

    if (bundle.hasRequirements() && bundle.requirementContext().target() != null) {
      appendRequirementContext(sb, bundle.requirementContext());
    }
    if (bundle.hasExecutionHistory()) {
      sb.append("Pass rate (7d): ")
          .append(String.format("%.0f%%", bundle.executionContext().passRate7d() * 100))
          .append("\n");
    }
    if (bundle.releaseVersion() != null) {
      sb.append("Release: ").append(bundle.releaseVersion()).append("\n");
    }
    if (bundle.trigger() != null && bundle.trigger().refUrl() != null) {
      sb.append("Trigger URL: ").append(bundle.trigger().refUrl()).append("\n");
    }
    if (bundle.trigger() != null && bundle.trigger().entityExternalId() != null) {
      sb.append("Entity: ")
          .append(bundle.trigger().entityType())
          .append(" #")
          .append(bundle.trigger().entityExternalId())
          .append("\n");
    }
    if (bundle.hasPrDiff()) {
      // Inline the pre-fetched changed files list so Claude doesn't need a tool call for it
      BlobRef ref = bundle.prDiff();
      blobStore
          .fetchText(ref)
          .ifPresentOrElse(
              content ->
                  sb.append("\nPR changed files (pre-fetched):\n").append(content).append("\n"),
              () -> sb.append("\nPR diff blob key: ").append(ref.key()).append("\n"));
    }
    return sb.toString();
  }

  /**
   * Renders the full requirement hierarchy into the user prompt so Claude understands the
   * Epic→Story→Subtask chain, sibling scope, cross-links, and release context. Format is structured
   * but token-efficient: indented tree, then cross-links, then release tags.
   */
  private void appendRequirementContext(
      StringBuilder sb, com.platform.common.agent.RequirementContext ctx) {
    var target = ctx.target();

    // ── Target requirement ────────────────────────────────────────────────
    sb.append("\n## Requirement\n");
    sb.append("**[")
        .append(target.issueType())
        .append("] ")
        .append(target.externalId() != null ? target.externalId() + " — " : "")
        .append(target.title())
        .append("**\n");
    if (target.description() != null && !target.description().isBlank()) {
      sb.append(target.description().trim()).append("\n");
    }
    if (!target.acceptanceCriteria().isEmpty()) {
      sb.append("\nAcceptance criteria:\n");
      target.acceptanceCriteria().forEach(ac -> sb.append("- ").append(ac.text()).append("\n"));
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
      ctx.children()
          .forEach(
              ch -> {
                sb.append("- [").append(ch.issueType()).append("] ");
                if (ch.externalId() != null) sb.append(ch.externalId()).append(" — ");
                sb.append(ch.title()).append("\n");
              });
    }

    // ── Cross-links ───────────────────────────────────────────────────────
    if (!ctx.links().isEmpty()) {
      sb.append("\n## Related requirements\n");
      ctx.links()
          .forEach(
              lk -> {
                String rel =
                    lk.linkSubtype() != null
                        ? lk.linkSubtype().name().toLowerCase().replace('_', ' ')
                        : lk.edgeType().name().toLowerCase().replace('_', ' ');
                sb.append("- ")
                    .append(rel)
                    .append(": [")
                    .append(lk.targetExternalId())
                    .append("] ")
                    .append(lk.targetTitle())
                    .append("\n");
              });
    }

    // ── Release / sprint scope ────────────────────────────────────────────
    if (!ctx.releaseScope().isEmpty()) {
      sb.append("\nRelease scope: ").append(String.join(", ", ctx.releaseScope())).append("\n");
    }

    sb.append("\n");
  }

  private String saveCheckpoint(
      ContextBundle bundle, List<ChatMessage> messages, TokenUsage usage) {
    try {
      // Use LangChain4j's codec so the polymorphic message hierarchy round-trips on resume.
      String messagesJson = ChatMessageSerializer.messagesToJson(messages);
      var blob =
          blobStore.storeText(
              BlobStoreBuckets.CHECKPOINTS,
              messagesJson,
              com.platform.common.storage.BlobRef.TYPE_JSON);
      var state =
          new CheckpointService.ConversationState(
              bundle.sessionId().toString(),
              blob,
              null,
              null,
              List.of(),
              bundle.resumeStrategy() != null ? bundle.resumeStrategy() : ResumeStrategy.COMPRESSED,
              java.time.Instant.now());
      return checkpointService.save(bundle, state, state.strategy());
    } catch (Exception e) {
      log.error("failed to save checkpoint", e);
      return UUID.randomUUID().toString();
    }
  }
}
