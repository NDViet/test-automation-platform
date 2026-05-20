package com.platform.agent.node.impl;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.common.agent.*;
import com.platform.common.integration.IntegrationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * HEALING node — diagnoses a failing test, reads its source from GitHub, generates a minimal
 * fix using Claude, then proposes a commit + draft PR for human approval before merging.
 *
 * Flow:
 *   1. ContextBundle.executionContext contains recent failure samples (stackTrace, failureMessage)
 *   2. Claude calls github_read_file to read the broken test source
 *   3. Claude calls github_commit_file to commit the fix to a feature branch
 *   4. Claude calls github_create_pr to open a draft PR
 *   5. Claude calls request_review — HITL pause before the PR is un-drafted
 *
 * Task type: PROPOSE_HEAL_FIX (propose + review; separate COMMIT_HEAL_FIX runs after approval)
 */
@Component
public class HealingNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(HealingNode.class);

    private final AgentOrchestrator orchestrator;
    private final GitHubApiClient gitHubApiClient;
    private final ObjectMapper mapper;

    public HealingNode(AgentOrchestrator orchestrator,
                       GitHubApiClient gitHubApiClient,
                       ObjectMapper mapper) {
        this.orchestrator    = orchestrator;
        this.gitHubApiClient = gitHubApiClient;
        this.mapper          = mapper;
    }

    @Override
    public AgentTaskType taskType() { return AgentTaskType.PROPOSE_HEAL_FIX; }

    @Override
    public NodeType nodeType() { return NodeType.HEALING; }

    @Override
    public NodeResult execute(ContextBundle bundle) {
        return orchestrator.run(bundle, this);
    }

    @Override
    public List<Tool> tools() {
        return List.of(

                Tool.builder()
                        .name("github_read_file")
                        .description("Read a file from a GitHub repository. Returns the decoded source code " +
                                "prefixed with its blob SHA (needed if you later update the file).")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "owner", Map.of("type", "string", "description", "Repository owner (org or user)"),
                                        "repo",  Map.of("type", "string", "description", "Repository name"),
                                        "path",  Map.of("type", "string", "description", "File path within the repo (e.g. src/test/java/com/example/FooTest.java)"),
                                        "ref",   Map.of("type", "string", "description", "Branch name or commit SHA; omit to use the default branch")
                                )))
                                .addRequired("owner").addRequired("repo").addRequired("path")
                                .build())
                        .build(),

                Tool.builder()
                        .name("github_commit_file")
                        .description("Commit a file to a branch on GitHub. The content should be the complete " +
                                "fixed file as plain text (not Base64). The sha must be the blob SHA returned by " +
                                "github_read_file — omit for new files.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "owner",   Map.of("type", "string",  "description", "Repository owner"),
                                        "repo",    Map.of("type", "string",  "description", "Repository name"),
                                        "path",    Map.of("type", "string",  "description", "File path within the repo"),
                                        "message", Map.of("type", "string",  "description", "Commit message"),
                                        "content", Map.of("type", "string",  "description", "Complete new file content as plain text"),
                                        "sha",     Map.of("type", "string",  "description", "Blob SHA of the existing file (from github_read_file); omit when creating a new file"),
                                        "branch",  Map.of("type", "string",  "description", "Branch to commit to; must exist")
                                )))
                                .addRequired("owner").addRequired("repo").addRequired("path")
                                .addRequired("message").addRequired("content").addRequired("branch")
                                .build())
                        .build(),

                Tool.builder()
                        .name("github_create_pr")
                        .description("Open a draft pull request on GitHub. Always creates as draft — " +
                                "the PR is un-drafted only after human approval.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "owner", Map.of("type", "string", "description", "Repository owner"),
                                        "repo",  Map.of("type", "string", "description", "Repository name"),
                                        "title", Map.of("type", "string", "description", "PR title"),
                                        "head",  Map.of("type", "string", "description", "Source branch containing the fix"),
                                        "base",  Map.of("type", "string", "description", "Target branch (usually 'main' or 'master')"),
                                        "body",  Map.of("type", "string", "description", "PR description explaining the fix and failure context")
                                )))
                                .addRequired("owner").addRequired("repo").addRequired("title")
                                .addRequired("head").addRequired("base").addRequired("body")
                                .build())
                        .build(),

                Tool.builder()
                        .name("request_review")
                        .description("Pause execution and send the proposed fix to a human reviewer. " +
                                "Call this after creating the draft PR, with a summary and the PR URL.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "summary", Map.of("type", "string", "description", "Brief description of the fix and confidence level"),
                                        "payload", Map.of("type", "string", "description", "PR URL and key details for the reviewer")
                                )))
                                .addRequired("summary").addRequired("payload")
                                .build())
                        .build()
        );
    }

    @Override
    public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
        try {
            return switch (toolName) {
                case "github_read_file"    -> handleReadFile(inputJson, bundle);
                case "github_commit_file"  -> handleCommitFile(inputJson, bundle);
                case "github_create_pr"    -> handleCreatePr(inputJson, bundle);
                case "request_review"      -> handleRequestReview(inputJson);
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
        String owner   = input.path("owner").asText();
        String repo    = input.path("repo").asText();
        String path    = input.path("path").asText();
        String ref     = input.path("ref").asText("");
        String token   = resolveToken(bundle);

        String rawJson = gitHubApiClient.getFileContent(owner, repo, path, ref, token);
        JsonNode resp  = mapper.readTree(rawJson);

        String sha         = resp.path("sha").asText("");
        String encoded     = resp.path("content").asText("").replace("\n", "");
        String decodedCode = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

        // Prefix with sha so Claude can pass it to github_commit_file
        return "// sha: " + sha + "\n" + decodedCode;
    }

    private String handleCommitFile(String inputJson, ContextBundle bundle) throws Exception {
        JsonNode input   = mapper.readTree(inputJson);
        String owner     = input.path("owner").asText();
        String repo      = input.path("repo").asText();
        String path      = input.path("path").asText();
        String message   = input.path("message").asText();
        String content   = input.path("content").asText();
        String sha       = input.path("sha").asText(null);
        String branch    = input.path("branch").asText();
        String token     = resolveToken(bundle);

        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String shaArg  = (sha == null || sha.isBlank()) ? null : sha;

        gitHubApiClient.createOrUpdateFile(owner, repo, path, message, encoded, shaArg, branch, token);
        log.info("HealingNode: committed {} to {}/{} branch={}", path, owner, repo, branch);
        return "File committed to branch '" + branch + "': " + path;
    }

    private String handleCreatePr(String inputJson, ContextBundle bundle) throws Exception {
        JsonNode input = mapper.readTree(inputJson);
        String owner   = input.path("owner").asText();
        String repo    = input.path("repo").asText();
        String title   = input.path("title").asText();
        String head    = input.path("head").asText();
        String base    = input.path("base").asText("main");
        String body    = input.path("body").asText();
        String token   = resolveToken(bundle);

        String rawJson = gitHubApiClient.createPullRequest(owner, repo, title, head, base, body, true, token);
        JsonNode resp  = mapper.readTree(rawJson);
        String prUrl   = resp.path("html_url").asText("(url unavailable)");
        log.info("HealingNode: draft PR created → {}", prUrl);
        return "Pull request created: " + prUrl;
    }

    private String handleRequestReview(String inputJson) throws Exception {
        JsonNode input = mapper.readTree(inputJson);
        String payload = input.path("payload").asText("");
        return "__AWAITING_REVIEW__ " + payload;
    }

    private String resolveToken(ContextBundle bundle) {
        if (bundle.credentials() == null) return "";
        String t = bundle.credentials().token(IntegrationType.GITHUB);
        return t != null ? t : "";
    }
}
