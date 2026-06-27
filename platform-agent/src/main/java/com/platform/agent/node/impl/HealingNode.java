package com.platform.agent.node.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.common.agent.*;
import com.platform.common.integration.IntegrationType;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HEALING node — diagnoses a failing test, reads its source from GitHub, generates a minimal fix
 * using Claude, then proposes a commit + draft PR for human approval before merging.
 *
 * <p>Flow: 1. ContextBundle.executionContext contains recent failure samples (stackTrace,
 * failureMessage) 2. Claude calls github_read_file to read the broken test source 3. Claude calls
 * github_commit_file to commit the fix to a feature branch 4. Claude calls github_create_pr to open
 * a draft PR 5. Claude calls request_review — HITL pause before the PR is un-drafted
 *
 * <p>Task type: PROPOSE_HEAL_FIX (propose + review; separate COMMIT_HEAL_FIX runs after approval)
 */
@Component
public class HealingNode implements AgentNode {

  private static final Logger log = LoggerFactory.getLogger(HealingNode.class);

  private final AgentOrchestrator orchestrator;
  private final GitHubApiClient gitHubApiClient;
  private final com.platform.agent.node.tools.IntegrationTokenResolver tokenResolver;
  private final ObjectMapper mapper;

  public HealingNode(
      AgentOrchestrator orchestrator,
      GitHubApiClient gitHubApiClient,
      com.platform.agent.node.tools.IntegrationTokenResolver tokenResolver,
      ObjectMapper mapper) {
    this.orchestrator = orchestrator;
    this.gitHubApiClient = gitHubApiClient;
    this.tokenResolver = tokenResolver;
    this.mapper = mapper;
  }

  @Override
  public AgentTaskType taskType() {
    return AgentTaskType.PROPOSE_HEAL_FIX;
  }

  @Override
  public NodeType nodeType() {
    return NodeType.HEALING;
  }

  @Override
  public NodeResult execute(ContextBundle bundle) {
    return orchestrator.run(bundle, this);
  }

  @Override
  public List<ToolSpecification> toolSpecs() {
    return List.of(
        ToolSpecification.builder()
            .name("github_read_file")
            .description(
                "Read a file from a GitHub repository; returns the decoded source prefixed with its"
                    + " blob SHA.")
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("owner", "Repository owner (org or user)")
                    .addStringProperty("repo", "Repository name")
                    .addStringProperty("path", "File path within the repo")
                    .addStringProperty("ref", "Branch name or commit SHA; omit for default branch")
                    .required("owner", "repo", "path")
                    .build())
            .build(),
        ToolSpecification.builder()
            .name("github_commit_file")
            .description(
                "Commit a file to a branch. content is the complete file as plain text; sha is the"
                    + " blob SHA from github_read_file (omit for new files).")
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("owner", "Repository owner")
                    .addStringProperty("repo", "Repository name")
                    .addStringProperty("path", "File path within the repo")
                    .addStringProperty("message", "Commit message")
                    .addStringProperty("content", "Complete new file content as plain text")
                    .addStringProperty("sha", "Blob SHA of the existing file; omit when creating")
                    .addStringProperty("branch", "Branch to commit to; must exist")
                    .required("owner", "repo", "path", "message", "content", "branch")
                    .build())
            .build(),
        ToolSpecification.builder()
            .name("github_create_pr")
            .description("Open a draft pull request. Always creates as draft until human approval.")
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("owner", "Repository owner")
                    .addStringProperty("repo", "Repository name")
                    .addStringProperty("title", "PR title")
                    .addStringProperty("head", "Source branch containing the fix")
                    .addStringProperty("base", "Target branch (usually 'main')")
                    .addStringProperty("body", "PR description explaining the fix")
                    .required("owner", "repo", "title", "head", "base", "body")
                    .build())
            .build(),
        ToolSpecification.builder()
            .name("request_review")
            .description("Pause execution and request human review.")
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("summary", "Brief description of what is being reviewed")
                    .addStringProperty("payload", "Full details for the reviewer")
                    .required("summary", "payload")
                    .build())
            .build());
  }

  @Override
  public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
    try {
      return switch (toolName) {
        case "github_read_file" -> handleReadFile(inputJson, bundle);
        case "github_commit_file" -> handleCommitFile(inputJson, bundle);
        case "github_create_pr" -> handleCreatePr(inputJson, bundle);
        case "request_review" -> handleRequestReview(inputJson);
        default -> "Unknown tool: " + toolName;
      };
    } catch (Exception e) {
      log.error("HealingNode tool '{}' failed: {}", toolName, e.getMessage(), e);
      return "Error: " + e.getMessage();
    }
  }

  // -------------------------------------------------------------------------

  private String handleReadFile(String inputJson, ContextBundle bundle) throws Exception {
    JsonNode input = mapper.readTree(inputJson);
    String owner = input.path("owner").asText();
    String repo = input.path("repo").asText();
    String path = input.path("path").asText();
    String ref = input.path("ref").asText("");
    String token = resolveToken(bundle);

    String rawJson = gitHubApiClient.getFileContent(owner, repo, path, ref, token);
    JsonNode resp = mapper.readTree(rawJson);

    String sha = resp.path("sha").asText("");
    String encoded = resp.path("content").asText("").replace("\n", "");
    String decodedCode = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

    // Prefix with sha so Claude can pass it to github_commit_file
    return "// sha: " + sha + "\n" + decodedCode;
  }

  private String handleCommitFile(String inputJson, ContextBundle bundle) throws Exception {
    JsonNode input = mapper.readTree(inputJson);
    String owner = input.path("owner").asText();
    String repo = input.path("repo").asText();
    String path = input.path("path").asText();
    String message = input.path("message").asText();
    String content = input.path("content").asText();
    String sha = input.path("sha").asText(null);
    String branch = input.path("branch").asText();
    String token = resolveToken(bundle);

    String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    String shaArg = (sha == null || sha.isBlank()) ? null : sha;

    gitHubApiClient.createOrUpdateFile(owner, repo, path, message, encoded, shaArg, branch, token);
    log.info("HealingNode: committed {} to {}/{} branch={}", path, owner, repo, branch);
    return "File committed to branch '" + branch + "': " + path;
  }

  private String handleCreatePr(String inputJson, ContextBundle bundle) throws Exception {
    JsonNode input = mapper.readTree(inputJson);
    String owner = input.path("owner").asText();
    String repo = input.path("repo").asText();
    String title = input.path("title").asText();
    String head = input.path("head").asText();
    String base = input.path("base").asText("main");
    String body = input.path("body").asText();
    String token = resolveToken(bundle);

    String rawJson =
        gitHubApiClient.createPullRequest(owner, repo, title, head, base, body, true, token);
    JsonNode resp = mapper.readTree(rawJson);
    String prUrl = resp.path("html_url").asText("(url unavailable)");
    log.info("HealingNode: draft PR created → {}", prUrl);
    return "Pull request created: " + prUrl;
  }

  private String handleRequestReview(String inputJson) throws Exception {
    JsonNode input = mapper.readTree(inputJson);
    String payload = input.path("payload").asText("");
    return "__AWAITING_REVIEW__ " + payload;
  }

  private String resolveToken(ContextBundle bundle) {
    if (bundle.credentials() != null) {
      String t = bundle.credentials().token(IntegrationType.GITHUB);
      if (t != null && !t.isBlank()) return t;
    }
    if (bundle.projectId() != null) {
      return tokenResolver.resolveToken(bundle.projectId(), IntegrationType.GITHUB).orElse("");
    }
    return "";
  }
}
