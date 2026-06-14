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
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.domain.TestCaseStep;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.repository.TestCaseStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * AUTOMATION_GEN node that generates automated test code from an approved manual test case
 * and creates a GitHub pull request with the generated test file.
 *
 * Tools available to Claude:
 *   - github_create_file: creates a new test file on a branch
 *   - github_create_pr:   creates a draft PR for review
 */
@Component
public class AutomationCodeGenerationNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(AutomationCodeGenerationNode.class);

    private final AgentOrchestrator orchestrator;
    private final PlatformTestCaseRepository testCaseRepo;
    private final TestCaseStepRepository stepRepo;
    private final ProjectIntegrationConfigRepository configRepo;
    private final PlatformRequirementRepository requirementRepo;
    private final GitHubApiClient gitHubApiClient;
    private final com.platform.agent.node.tools.IntegrationTokenResolver tokenResolver;
    private final ObjectMapper mapper;

    public AutomationCodeGenerationNode(AgentOrchestrator orchestrator,
                                         PlatformTestCaseRepository testCaseRepo,
                                         TestCaseStepRepository stepRepo,
                                         ProjectIntegrationConfigRepository configRepo,
                                         PlatformRequirementRepository requirementRepo,
                                         GitHubApiClient gitHubApiClient,
                                         com.platform.agent.node.tools.IntegrationTokenResolver tokenResolver,
                                         ObjectMapper mapper) {
        this.orchestrator    = orchestrator;
        this.testCaseRepo    = testCaseRepo;
        this.stepRepo        = stepRepo;
        this.configRepo      = configRepo;
        this.requirementRepo = requirementRepo;
        this.gitHubApiClient = gitHubApiClient;
        this.tokenResolver   = tokenResolver;
        this.mapper          = mapper;
    }

    @Override
    public AgentTaskType taskType() { return AgentTaskType.GENERATE_AUTOMATION_CODE; }

    @Override
    public NodeType nodeType() { return NodeType.AUTOMATION_GEN; }

    @Override
    public String systemPrompt(ContextBundle bundle) {
        return """
                You are the AutomationCodeGenerationNode — a QA automation engineer.

                Project: %s (ID: %s)

                ## Your job
                Given a manual test case with steps, generate a well-structured automated test using the project's test framework.
                Then create a GitHub PR with the generated test file.

                ## Tools available
                - github_create_file: creates a new test file in the repo
                - github_create_pr: creates a PR for review

                ## Rules
                - Generate clean, readable test code (prefer Java/JUnit5 or TypeScript/Playwright based on the repo's language)
                - Each manual step becomes a test assertion or action
                - Name the test file meaningfully based on the test case title
                - Commit to a new branch named: `test/auto-{testCaseId}`
                - PR title: "test: Auto-generated test for {testCaseTitle}"
                - PR body: include the test case steps as a reference comment
                """.formatted(bundle.projectSlug(), bundle.projectId());
    }

    @Override
    @Transactional
    public NodeResult execute(ContextBundle bundle) {
        UUID projectId = bundle.projectId();

        // 1. Extract testCaseId from trigger's entityExternalId
        TriggerRef trigger = bundle.trigger();
        if (trigger == null || trigger.entityExternalId() == null) {
            return NodeResult.failed(bundle.sessionId(), bundle.workflowId(),
                    nodeType(), taskType(),
                    "MISSING_TEST_CASE_ID", "No testCaseId in trigger.entityExternalId",
                    TokenUsage.zero());
        }

        UUID testCaseId;
        try {
            testCaseId = UUID.fromString(trigger.entityExternalId().trim());
        } catch (IllegalArgumentException e) {
            return NodeResult.failed(bundle.sessionId(), bundle.workflowId(),
                    nodeType(), taskType(),
                    "INVALID_TEST_CASE_ID",
                    "Invalid testCaseId: " + trigger.entityExternalId(),
                    TokenUsage.zero());
        }

        // 2. Load test case
        Optional<PlatformTestCase> tcOpt = testCaseRepo.findById(testCaseId);
        if (tcOpt.isEmpty()) {
            return NodeResult.failed(bundle.sessionId(), bundle.workflowId(),
                    nodeType(), taskType(),
                    "TEST_CASE_NOT_FOUND",
                    "Test case not found: " + testCaseId,
                    TokenUsage.zero());
        }
        PlatformTestCase tc = tcOpt.get();

        // 3. Load steps
        List<TestCaseStep> steps = stepRepo.findByTestCaseIdOrderByStepNumberAsc(testCaseId);

        // 4. Load GitHub config for this project
        List<ProjectIntegrationConfig> githubConfigs =
                configRepo.findAllByProjectIdAndIntegrationType(projectId, "GITHUB");

        ProjectIntegrationConfig githubConfig = resolveGithubConfig(githubConfigs, tc);
        if (githubConfig == null) {
            log.warn("AutomationCodeGenerationNode: no enabled GitHub config for project {}", projectId);
        }

        // 5. Mark test case as generating
        tc.markAutomationGenerating();
        testCaseRepo.save(tc);

        // 6. Build user message describing the test case + steps
        String userMessage = buildTestCaseMessage(tc, steps, githubConfig, testCaseId);

        // 7. Store context for tool dispatch (used inside dispatchToolCall)
        //    We pass it through a shim that holds this state and delegates tool calls back here.
        AutomationShimNode shim = new AutomationShimNode(this, tc, githubConfig, testCaseId);
        NodeResult claudeResult = orchestrator.run(bundle, shim);

        // 8. After Claude finishes, reload the test case to check final automation status.
        //    The dispatchToolCall for github_create_pr updates it. If status is still GENERATING
        //    (i.e. Claude didn't call github_create_pr or it failed), mark as FAILED.
        PlatformTestCase reloaded = testCaseRepo.findById(testCaseId).orElse(tc);
        if ("GENERATING".equals(reloaded.getAutomationStatus())) {
            log.warn("AutomationCodeGenerationNode: PR was not created for test case {}, marking FAILED",
                    testCaseId);
            reloaded.markAutomationFailed();
            testCaseRepo.save(reloaded);
        }

        if (claudeResult.hasFailed()) {
            return claudeResult;
        }

        String prUrl = reloaded.getAutomationPrUrl();
        String summary = prUrl != null
                ? "Automation PR created for test case '" + tc.getTitle() + "': " + prUrl
                : "Automation code generation attempted for test case '" + tc.getTitle() +
                  "' but no PR URL was captured.";

        return NodeResult.completed(bundle.sessionId(), bundle.workflowId(),
                nodeType(), taskType(),
                ArtifactManifest.empty(),
                summary,
                claudeResult.tokenUsage());
    }

    // -------------------------------------------------------------------------
    // Tool definitions
    // -------------------------------------------------------------------------

    @Override
    public List<Tool> tools() {
        return List.of(

                Tool.builder()
                        .name("github_create_file")
                        .description("Creates a new file in the GitHub repository on the specified branch. " +
                                "Content must be Base64-encoded. Creates the branch if it does not exist.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "owner",   Map.of("type", "string", "description", "GitHub repository owner"),
                                        "repo",    Map.of("type", "string", "description", "GitHub repository name"),
                                        "path",    Map.of("type", "string", "description", "File path within the repo"),
                                        "content", Map.of("type", "string", "description", "File content encoded as Base64"),
                                        "message", Map.of("type", "string", "description", "Commit message"),
                                        "branch",  Map.of("type", "string", "description", "Branch to commit to")
                                )))
                                .addRequired("owner")
                                .addRequired("repo")
                                .addRequired("path")
                                .addRequired("content")
                                .addRequired("message")
                                .addRequired("branch")
                                .build())
                        .build(),

                Tool.builder()
                        .name("github_create_pr")
                        .description("Creates a draft pull request in the GitHub repository.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "owner", Map.of("type", "string", "description", "GitHub repository owner"),
                                        "repo",  Map.of("type", "string", "description", "GitHub repository name"),
                                        "title", Map.of("type", "string", "description", "PR title"),
                                        "head",  Map.of("type", "string", "description", "Source branch (the branch with changes)"),
                                        "base",  Map.of("type", "string", "description", "Target branch (e.g. 'main')"),
                                        "body",  Map.of("type", "string", "description", "PR body in markdown")
                                )))
                                .addRequired("owner")
                                .addRequired("repo")
                                .addRequired("title")
                                .addRequired("head")
                                .addRequired("base")
                                .addRequired("body")
                                .build())
                        .build()
        );
    }

    @Override
    public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
        // Should not be called directly — AutomationShimNode delegates here
        return "Tool '" + toolName + "' dispatch requires shim context.";
    }

    // -------------------------------------------------------------------------
    // Tool handlers (called from AutomationShimNode which provides tc/config context)
    // -------------------------------------------------------------------------

    String handleCreateFile(String inputJson, ContextBundle bundle,
                            ProjectIntegrationConfig githubConfig) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            String owner   = input.path("owner").asText(resolveOwner(githubConfig));
            String repo    = input.path("repo").asText(resolveRepo(githubConfig));
            String path    = input.path("path").asText();
            String content = input.path("content").asText(); // Base64
            String message = input.path("message").asText("Add auto-generated test");
            String branch  = input.path("branch").asText("main");
            String token   = resolveToken(bundle, githubConfig);

            log.info("AutomationCodeGenerationNode: creating file {}/{}/{}@{}", owner, repo, path, branch);
            return gitHubApiClient.createOrUpdateFile(owner, repo, path, message, content, null, branch, token);
        } catch (Exception e) {
            log.error("github_create_file failed: {}", e.getMessage(), e);
            return "Error creating file: " + e.getMessage();
        }
    }

    String handleCreatePr(String inputJson, ContextBundle bundle,
                          PlatformTestCase tc, ProjectIntegrationConfig githubConfig) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            String owner   = input.path("owner").asText(resolveOwner(githubConfig));
            String repo    = input.path("repo").asText(resolveRepo(githubConfig));
            String title   = input.path("title").asText();
            String head    = input.path("head").asText();
            String base    = input.path("base").asText("main");
            String body    = input.path("body").asText("");
            String token   = resolveToken(bundle, githubConfig);

            log.info("AutomationCodeGenerationNode: creating PR for {}/{} head={}", owner, repo, head);
            String prJson = gitHubApiClient.createPullRequest(owner, repo, title, head, base, body, true, token);

            // Extract PR URL from response and update the test case
            try {
                JsonNode prNode = mapper.readTree(prJson);
                String prUrl = prNode.path("html_url").asText(null);
                if (prUrl != null && !prUrl.isBlank()) {
                    tc.markAutomationPrCreated(prUrl);
                    testCaseRepo.save(tc);
                    log.info("AutomationCodeGenerationNode: PR created at {}", prUrl);
                    return "PR created successfully: " + prUrl;
                }
            } catch (Exception parseEx) {
                log.warn("AutomationCodeGenerationNode: could not parse PR response: {}", parseEx.getMessage());
            }

            return prJson;
        } catch (Exception e) {
            log.error("github_create_pr failed: {}", e.getMessage(), e);
            tc.markAutomationFailed();
            testCaseRepo.save(tc);
            return "Error creating PR: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildTestCaseMessage(PlatformTestCase tc, List<TestCaseStep> steps,
                                         ProjectIntegrationConfig githubConfig, UUID testCaseId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate automated test code and create a GitHub PR for the following manual test case.\n\n");
        sb.append("## Test Case\n");
        sb.append("ID: ").append(testCaseId).append("\n");
        sb.append("Title: ").append(tc.getTitle()).append("\n");
        if (tc.getDescription() != null) sb.append("Description: ").append(tc.getDescription()).append("\n");
        if (tc.getPreconditions() != null) sb.append("Preconditions: ").append(tc.getPreconditions()).append("\n");
        sb.append("Priority: ").append(tc.getPriority()).append("\n");

        // Linked requirements — provide full context so generated test code is correctly scoped
        List<String> linkedReqIds = tc.getLinkedRequirementIds();
        if (linkedReqIds != null && !linkedReqIds.isEmpty()) {
            sb.append("\n## Linked Requirements (source of truth for what to test)\n");
            for (String reqIdStr : linkedReqIds) {
                try {
                    UUID reqId = UUID.fromString(reqIdStr);
                    requirementRepo.findById(reqId).ifPresent(req -> {
                        sb.append("### ").append(req.getTitle()).append("\n");
                        if (req.getDescription() != null && !req.getDescription().isBlank()) {
                            sb.append(req.getDescription().trim()).append("\n");
                        }
                        if (req.getAcceptanceCriteria() != null && !req.getAcceptanceCriteria().isEmpty()) {
                            sb.append("Acceptance Criteria:\n");
                            for (Object ac : req.getAcceptanceCriteria()) {
                                sb.append("  - ").append(ac).append("\n");
                            }
                        }
                        sb.append("\n");
                    });
                } catch (IllegalArgumentException ignored) {}
            }
        }

        sb.append("\n## Steps\n");
        for (TestCaseStep step : steps) {
            sb.append(step.getStepNumber()).append(". Action: ").append(step.getAction()).append("\n");
            if (step.getExpectedResult() != null) {
                sb.append("   Expected: ").append(step.getExpectedResult()).append("\n");
            }
            if (step.getNotes() != null && !step.getNotes().isBlank()) {
                sb.append("   Notes: ").append(step.getNotes()).append("\n");
            }
        }

        if (githubConfig != null) {
            String owner = resolveOwner(githubConfig);
            String repo  = resolveRepo(githubConfig);
            if (owner != null && repo != null) {
                sb.append("\n## GitHub Target\n");
                sb.append("Repository: ").append(owner).append("/").append(repo).append("\n");
                sb.append("Base branch: ").append(resolveBaseBranch(githubConfig)).append("\n");
            }
        }

        sb.append("\nBranch name to use: test/auto-").append(testCaseId).append("\n");
        sb.append("Remember to Base64-encode the file content before calling github_create_file.\n");
        return sb.toString();
    }

    private ProjectIntegrationConfig resolveGithubConfig(List<ProjectIntegrationConfig> configs,
                                                          PlatformTestCase tc) {
        if (configs.isEmpty()) return null;

        // Prefer the config referenced by the test case's automationGithubConfigId
        if (tc.getAutomationGithubConfigId() != null) {
            Optional<ProjectIntegrationConfig> preferred = configs.stream()
                    .filter(c -> c.getId().equals(tc.getAutomationGithubConfigId()))
                    .findFirst();
            if (preferred.isPresent()) return preferred.get();
        }

        // Fall back to the first enabled config
        return configs.stream().filter(ProjectIntegrationConfig::isEnabled).findFirst().orElse(null);
    }

    private String resolveOwner(ProjectIntegrationConfig config) {
        if (config == null) return null;
        String fullName = config.param("repoFullName");
        if (fullName != null && fullName.contains("/")) {
            return fullName.split("/")[0];
        }
        return config.param("owner");
    }

    private String resolveRepo(ProjectIntegrationConfig config) {
        if (config == null) return null;
        String fullName = config.param("repoFullName");
        if (fullName != null && fullName.contains("/")) {
            return fullName.split("/", 2)[1];
        }
        return config.param("repo");
    }

    private String resolveBaseBranch(ProjectIntegrationConfig config) {
        if (config == null) return "main";
        String branch = config.param("defaultBranch");
        return branch != null && !branch.isBlank() ? branch : "main";
    }

    private String resolveToken(ContextBundle bundle, ProjectIntegrationConfig config) {
        // 1. Try session credentials first
        if (bundle.credentials() != null) {
            String token = bundle.credentials().token(IntegrationType.GITHUB);
            if (token != null && !token.isBlank()) return token;
        }
        // 2. Org→Team→Project encrypted credential cascade
        if (bundle.projectId() != null) {
            Optional<String> cascade = tokenResolver.resolveToken(bundle.projectId(), IntegrationType.GITHUB);
            if (cascade.isPresent()) return cascade.get();
        }
        // 3. Fall back to legacy plaintext config-level token
        if (config != null) {
            String token = config.param("token");
            if (token != null && !token.isBlank()) return token;
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Shim: holds test case + GitHub config context for tool dispatch
    // -------------------------------------------------------------------------

    /**
     * Wraps AutomationCodeGenerationNode but carries test case and GitHub config
     * so dispatchToolCall can pass them to the handler methods.
     */
    private static class AutomationShimNode implements AgentNode {
        private final AutomationCodeGenerationNode parent;
        private final PlatformTestCase tc;
        private final ProjectIntegrationConfig githubConfig;
        private final UUID testCaseId;

        AutomationShimNode(AutomationCodeGenerationNode parent,
                            PlatformTestCase tc,
                            ProjectIntegrationConfig githubConfig,
                            UUID testCaseId) {
            this.parent       = parent;
            this.tc           = tc;
            this.githubConfig = githubConfig;
            this.testCaseId   = testCaseId;
        }

        @Override public AgentTaskType taskType() { return parent.taskType(); }
        @Override public NodeType nodeType()      { return parent.nodeType(); }

        @Override
        public String systemPrompt(ContextBundle bundle) {
            return parent.systemPrompt(bundle);
        }

        @Override
        public NodeResult execute(ContextBundle bundle) {
            return parent.orchestrator.run(bundle, this);
        }

        @Override
        public List<Tool> tools() { return parent.tools(); }

        @Override
        public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
            return switch (toolName) {
                case "github_create_file" -> parent.handleCreateFile(inputJson, bundle, githubConfig);
                case "github_create_pr"   -> parent.handleCreatePr(inputJson, bundle, tc, githubConfig);
                default -> "Unknown tool: " + toolName;
            };
        }
    }
}
